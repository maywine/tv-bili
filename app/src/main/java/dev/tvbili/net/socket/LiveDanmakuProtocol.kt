package dev.tvbili.net.socket

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterOutputStream

/**
 * B 站直播弹幕 WebSocket 协议编解码。
 *
 * 头 16 字节大端：
 * ```
 * [0-3]   Packet Length (含 header)
 * [4-5]   Header Length (固定 16)
 * [6-7]   Protocol Version (0=JSON, 1=heartbeat int32, 2=zlib, 3=brotli)
 * [8-11]  Operation (2=heartbeat, 3=heartbeat-reply, 5=message, 7=auth, 8=auth-reply)
 * [12-15] Sequence
 * [16-..] Body
 * ```
 *
 * 压缩包解出来后 payload 内仍是「连续多个完整 packet」，所以解压后**递归** [decode]。
 *
 * 移植自 BiliPai `core/network/socket/DanmakuProtocol.kt`，做了：
 * - 包内置长度边界校验，避免越界 crash
 * - 解压在 Dispatchers.Default 上跑，不阻塞 OkHttp WebSocket 线程
 */
object LiveDanmakuProtocol {

    const val HEAD_LENGTH = 16

    // 协议版本
    const val PROTO_VER_JSON = 0
    const val PROTO_VER_HEARTBEAT = 1
    const val PROTO_VER_ZLIB = 2
    const val PROTO_VER_BROTLI = 3

    // 操作码
    const val OP_HEARTBEAT = 2
    const val OP_HEARTBEAT_REPLY = 3
    const val OP_MESSAGE = 5
    const val OP_AUTH = 7
    const val OP_AUTH_REPLY = 8

    data class Packet(
        val version: Int,
        val operation: Int,
        val sequence: Int = 1,
        val body: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Packet) return false
            return version == other.version &&
                operation == other.operation &&
                sequence == other.sequence &&
                body.contentEquals(other.body)
        }

        override fun hashCode(): Int =
            (((version * 31 + operation) * 31) + sequence) * 31 + body.contentHashCode()
    }

    fun encode(packet: Packet): ByteArray {
        val total = HEAD_LENGTH + packet.body.size
        return ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(total)
            putShort(HEAD_LENGTH.toShort())
            putShort(packet.version.toShort())
            putInt(packet.operation)
            putInt(packet.sequence)
            if (packet.body.isNotEmpty()) put(packet.body)
        }.array()
    }

    suspend fun decode(data: ByteArray): List<Packet> = withContext(Dispatchers.Default) {
        if (data.size < HEAD_LENGTH) return@withContext emptyList()
        val packets = mutableListOf<Packet>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        while (buffer.remaining() >= HEAD_LENGTH) {
            val position = buffer.position()
            val totalLength = buffer.int
            if (totalLength < HEAD_LENGTH || totalLength > buffer.capacity() - position) break

            val headLength = buffer.short.toInt()
            val version = buffer.short.toInt()
            val operation = buffer.int
            val sequence = buffer.int

            if (headLength < HEAD_LENGTH || headLength > totalLength) break
            val bodyLength = totalLength - headLength
            if (bodyLength < 0 || bodyLength > buffer.remaining()) break

            val body = ByteArray(bodyLength)
            buffer.get(body)

            when (version) {
                PROTO_VER_JSON, PROTO_VER_HEARTBEAT -> {
                    packets += Packet(version, operation, sequence, body)
                }
                PROTO_VER_ZLIB -> runCatching {
                    packets.addAll(decode(decompressZlib(body)))
                }
                PROTO_VER_BROTLI -> runCatching {
                    packets.addAll(decode(decompressBrotli(body)))
                }
                else -> packets += Packet(version, operation, sequence, body)
            }
        }
        packets
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        InflaterOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decompressBrotli(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BrotliInputStream(ByteArrayInputStream(data)).use { input ->
            val buf = ByteArray(4096)
            var n: Int
            while (input.read(buf).also { n = it } != -1) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
