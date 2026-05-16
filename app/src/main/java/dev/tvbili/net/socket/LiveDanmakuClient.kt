package dev.tvbili.net.socket

import android.util.Log
import dev.tvbili.net.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.pow

/**
 * B 站直播弹幕 WebSocket 客户端。
 *
 * 设计要点：
 * - 复用 [NetworkModule.okHttpClient] 同一个 OkHttp，cookie / UA / 代理一致
 * - 串行解码（[incomingFrames] Channel）避免高频 onMessage 创建并发协程
 * - 背压（[_messageFlow] DROP_OLDEST 200）防止 UI 落后时积压爆内存
 * - 指数退避重连，认证失败时抑制重连风暴
 *
 * 移植自 BiliPai `core/network/socket/LiveDanmakuClient.kt`。
 */
class LiveDanmakuClient(
    private val scope: CoroutineScope,
) {

    private val tag = "LiveDanmakuClient"
    private var webSocket: WebSocket? = null

    private val _isConnected = AtomicBoolean(false)
    val isConnected: Boolean get() = _isConnected.get()

    private var retryCount = 0
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null

    private var currentHostUrl: String = ""
    private var currentAuthBody: String = ""
    private var suppressReconnect: Boolean = false

    private val incomingFrames = Channel<ByteArray>(
        capacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var decodeJob: Job? = null

    /** 业务消息流（OP_MESSAGE，已解压到 JSON 字节）。 */
    private val _messageFlow = MutableSharedFlow<LiveDanmakuProtocol.Packet>(
        replay = 0,
        extraBufferCapacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messageFlow = _messageFlow.asSharedFlow()

    init {
        startDecodeLoop()
    }

    /**
     * 连接。
     *
     * @param url wss://broadcastlv.chat.bilibili.com:443/sub（host 由 getDanmuInfo 返回）
     * @param token auth 包的 `key` 字段（[LiveDanmuInfoData.token]）
     * @param roomId 真实 room_id（短号要先 [LiveApi.getRoomInfo] 解析）
     * @param uid 当前登录 UID；未登录传 0
     */
    fun connect(url: String, token: String, roomId: Long, uid: Long = 0) {
        currentHostUrl = url
        // protover=2 用 Zlib，已被广泛验证；Brotli (3) 在部分老 Android 上有兼容问题
        currentAuthBody = JSONObject().apply {
            put("uid", uid)
            put("roomid", roomId)
            put("protover", 2)
            put("platform", "web")
            put("type", 2)
            put("key", token)
        }.toString()
        internalConnect()
    }

    fun disconnect() {
        Log.d(tag, "disconnect")
        suppressReconnect = true
        stopHeartbeat()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
        _isConnected.set(false)
    }

    private fun internalConnect() {
        // 注意：这里不能调 disconnect() —— 它会把 suppressReconnect 置 true 影响后续
        stopHeartbeat()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Reconnect")
        webSocket = null

        Log.d(tag, "connecting → $currentHostUrl")
        val request = Request.Builder()
            .url(currentHostUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            )
            .header("Origin", "https://live.bilibili.com")
            .build()
        webSocket = NetworkModule.okHttpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(tag, "open")
            _isConnected.set(true)
            retryCount = 0
            suppressReconnect = false
            sendAuth()
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!incomingFrames.trySend(bytes.toByteArray()).isSuccess) {
                Log.w(tag, "incoming frame dropped (backpressure)")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(tag, "closed code=$code reason=$reason")
            _isConnected.set(false)
            stopHeartbeat()
            if (code != 1000 && !suppressReconnect) scheduleReconnect()
            suppressReconnect = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(tag, "failure: ${t.message}")
            _isConnected.set(false)
            stopHeartbeat()
            if (!suppressReconnect) scheduleReconnect()
        }
    }

    private fun sendAuth() {
        val pkt = LiveDanmakuProtocol.Packet(
            version = LiveDanmakuProtocol.PROTO_VER_HEARTBEAT,
            operation = LiveDanmakuProtocol.OP_AUTH,
            body = currentAuthBody.toByteArray(),
        )
        send(pkt)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && isConnected) {
                val pkt = LiveDanmakuProtocol.Packet(
                    version = LiveDanmakuProtocol.PROTO_VER_HEARTBEAT,
                    operation = LiveDanmakuProtocol.OP_HEARTBEAT,
                    body = "[object Object]".toByteArray(),
                )
                send(pkt)
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val delayMs = min(1000.0 * 2.0.pow(retryCount), MAX_RETRY_DELAY_MS.toDouble()).toLong()
            Log.d(tag, "reconnect in ${delayMs}ms (attempt ${retryCount + 1})")
            delay(delayMs)
            retryCount++
            internalConnect()
        }
    }

    private fun send(packet: LiveDanmakuProtocol.Packet) {
        webSocket?.send(ByteString.of(*LiveDanmakuProtocol.encode(packet)))
    }

    private fun startDecodeLoop() {
        decodeJob?.cancel()
        decodeJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val frame = incomingFrames.receive()
                handleFrame(frame)
            }
        }
    }

    private suspend fun handleFrame(data: ByteArray) {
        runCatching {
            val packets = LiveDanmakuProtocol.decode(data)
            for (p in packets) {
                when (p.operation) {
                    LiveDanmakuProtocol.OP_HEARTBEAT_REPLY -> {
                        if (p.body.size >= 4) {
                            val popularity = ByteBuffer.wrap(p.body)
                                .order(ByteOrder.BIG_ENDIAN).int
                            Log.v(tag, "popularity=$popularity")
                        }
                    }
                    LiveDanmakuProtocol.OP_AUTH_REPLY -> {
                        val code = runCatching {
                            JSONObject(String(p.body, Charsets.UTF_8)).optInt("code", -1)
                        }.getOrDefault(-1)
                        if (code == 0) {
                            Log.d(tag, "auth ok")
                        } else {
                            Log.e(tag, "auth failed code=$code, suppress reconnect")
                            suppressReconnect = true
                            webSocket?.close(4001, "Auth Failed: $code")
                        }
                    }
                    LiveDanmakuProtocol.OP_MESSAGE -> {
                        _messageFlow.tryEmit(p)
                    }
                }
            }
        }.onFailure { e -> Log.e(tag, "handleFrame error: ${e.message}") }
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val MAX_RETRY_DELAY_MS = 10_000L
    }
}
