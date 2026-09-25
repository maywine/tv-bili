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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import dev.tvbili.net.hdApiParams
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

/** 使用 HD 客户端身份扫码，令牌与后续请求的 appkey 保持一致。 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()
    private var loginJob: Job? = null

    fun startLogin() {
        if (loginJob?.isActive == true || _state.value is LoginState.Success) return
        loadAppQr()
    }

    fun retry() = loadAppQr()

    private fun params(values: Map<String, String> = emptyMap()): Map<String, String> = hdApiParams(
        values + ("local_id" to (TokenStore.buvid3 ?: "0")), null, null, AppSignUtils.getTimestamp(),
    )

    private fun loadAppQr() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val response = NetworkModule.passportApi.generateAppQrCode(params())
                require(response.code == 0) { "二维码生成失败：${response.message} (${response.code})" }
                val data = requireNotNull(response.data) { "二维码信息为空" }
                val authCode = requireNotNull(data.authCode) { "二维码认证信息缺失" }
                val bitmap = generateQrBitmap(requireNotNull(data.url) { "二维码地址缺失" })
                _state.value = LoginState.QrReady(bitmap)
                pollSession(authCode, bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "HD login failed", e)
                _state.value = LoginState.Error(e.message ?: "登录失败")
            }
        }
    }

    private suspend fun pollSession(authCode: String, bitmap: Bitmap) {
        while (true) {
            delay(2000)
            val response = try {
                NetworkModule.passportApi.pollAppQrCode(params(mapOf("auth_code" to authCode)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "QR polling request failed", e)
                continue
            }
            when (response.code) {
                0 -> {
                    val data = requireNotNull(response.data) { "登录响应为空" }
                    val cookies = data.cookieInfo?.cookies.orEmpty()
                    val sess = cookies.firstOrNull { it.name == "SESSDATA" }?.value.orEmpty()
                    val jct = cookies.firstOrNull { it.name == "bili_jct" }?.value.orEmpty()
                    require(sess.isNotEmpty() && data.sessionAccessToken.isNotBlank()) { "登录凭据不完整，请重新扫码" }
                    TokenStore.saveSession(
                        context = getApplication(), sessdata = sess, biliJct = jct,
                        accessToken = data.sessionAccessToken, refreshToken = data.sessionRefreshToken,
                        mid = data.sessionMid, clientAppKey = AppSignUtils.HD_APP_KEY,
                    )
                    _state.value = LoginState.Success
                    return
                }
                86039 -> Unit
                86090 -> _state.value = LoginState.Scanned(bitmap)
                86038 -> {
                    _state.value = LoginState.Error("二维码已过期，请刷新")
                    return
                }
                else -> {
                    _state.value = LoginState.Error("扫码登录失败：${response.message} (${response.code})")
                    return
                }
            }
        }
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
