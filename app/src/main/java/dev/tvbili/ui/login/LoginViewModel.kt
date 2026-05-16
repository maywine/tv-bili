package dev.tvbili.ui.login

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.tvbili.data.store.TokenStore
import dev.tvbili.net.AppSignUtils
import dev.tvbili.net.NetworkModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 登录 UI 状态机。 */
sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class QrReady(val bitmap: Bitmap) : LoginState
    data class Scanned(val bitmap: Bitmap) : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

/**
 * TV 端二维码登录 ViewModel。
 *
 * 流程：[startLogin] → [loadTvQr] (生成 QR) → [startPolling] (2s 轮询) →
 *      [TokenStore.saveSession] → emit [LoginState.Success]
 *
 * 关键状态码（poll 接口）：
 * - 0 → 登录成功
 * - 86039 → 未确认（保持 QrReady）
 * - 86090 → 已扫码待确认（emit Scanned）
 * - 86038 → 过期（emit Error）
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var authCode: String = ""
    private var polling: Boolean = false
    private var currentBitmap: Bitmap? = null

    fun startLogin() {
        val s = _state.value
        if (s is LoginState.Loading || s is LoginState.QrReady) return
        loadTvQr()
    }

    fun retry() {
        polling = false
        loadTvQr()
    }

    override fun onCleared() {
        polling = false
        super.onCleared()
    }

    private fun loadTvQr() {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val params = buildTvParams(includeAuthCode = false)
                val signed = AppSignUtils.signForTvLogin(params)
                val resp = NetworkModule.passportApi.generateTvQrCode(signed)
                val data = resp.data
                    ?: error("TV QR generate failed: code=${resp.code} msg=${resp.message}")
                val url = data.url ?: error("TV QR url missing")
                authCode = data.authCode ?: error("TV QR auth_code missing")
                val bitmap = generateQrBitmap(url)
                currentBitmap = bitmap
                _state.value = LoginState.QrReady(bitmap)
                polling = true
                startPolling()
            } catch (e: Exception) {
                Log.e(TAG, "loadTvQr failed", e)
                _state.value = LoginState.Error(e.message ?: "网络错误")
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (polling) {
                delay(2000)
                try {
                    val params = buildTvParams(includeAuthCode = true)
                    val signed = AppSignUtils.signForTvLogin(params)
                    val resp = NetworkModule.passportApi.pollTvQrCode(signed)
                    when (resp.code) {
                        0 -> {
                            val d = resp.data ?: run {
                                _state.value = LoginState.Error("登录成功但 data 为空")
                                polling = false
                                return@launch
                            }
                            val cookies = d.cookieInfo?.cookies.orEmpty()
                            val sess = cookies.firstOrNull { it.name == "SESSDATA" }?.value.orEmpty()
                            val jct = cookies.firstOrNull { it.name == "bili_jct" }?.value.orEmpty()
                            if (sess.isEmpty()) {
                                _state.value = LoginState.Error("登录成功但 SESSDATA 缺失")
                                polling = false
                                return@launch
                            }
                            TokenStore.saveSession(
                                context = getApplication(),
                                sessdata = sess,
                                biliJct = jct,
                                accessToken = d.accessToken,
                                refreshToken = d.refreshToken,
                                mid = d.mid,
                            )
                            polling = false
                            _state.value = LoginState.Success
                        }
                        86039 -> {
                            // 未确认，保持 QrReady
                        }
                        86090 -> {
                            currentBitmap?.let { _state.value = LoginState.Scanned(it) }
                        }
                        86038 -> {
                            _state.value = LoginState.Error("二维码已过期，请刷新")
                            polling = false
                        }
                        else -> {
                            Log.w(TAG, "unknown poll code=${resp.code} msg=${resp.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "poll exception (will retry)", e)
                }
            }
        }
    }

    private fun buildTvParams(includeAuthCode: Boolean): Map<String, String> = buildMap {
        put("appkey", AppSignUtils.TV_APP_KEY)
        put("local_id", "0")
        put("ts", AppSignUtils.getTimestamp().toString())
        if (includeAuthCode) put("auth_code", authCode)
    }

    private fun generateQrBitmap(content: String): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val w = matrix.width
        val h = matrix.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        for (x in 0 until w) for (y in 0 until h) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}
