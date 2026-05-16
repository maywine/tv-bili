package dev.tvbili.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.model.NavData
import dev.tvbili.data.store.TokenStore
import dev.tvbili.net.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 个人中心 UI 状态机。 */
sealed interface ProfileState {
    data object Loading : ProfileState
    /** 已登录但 NavData 拉取失败时退化展示——至少能让用户「退出登录」。 */
    data class Ready(val data: NavData?) : ProfileState
    data class Error(val message: String) : ProfileState
}

/**
 * 个人中心 ViewModel。
 *
 * - [load]：拉 nav 接口（`x/web-interface/nav`），同步用户名/头像/等级/VIP
 * - [logout]：清 TokenStore + OkHttp cookieJar；回调里通知 MainActivity 切回 LoginScreen
 *
 * Nav 接口失败仍允许进入 Ready(null)——确保「退出登录」永远可用。
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun load() {
        _state.value = ProfileState.Loading
        viewModelScope.launch {
            runCatching { NetworkModule.mainApi.getNavInfo() }
                .onSuccess { resp ->
                    _state.value = ProfileState.Ready(resp.data)
                }
                .onFailure { e ->
                    Log.w(TAG, "nav fetch failed (fallback to Ready(null))", e)
                    _state.value = ProfileState.Ready(null)
                }
        }
    }

    /** 退出登录：清持久化凭据 + 内存 cookieJar；onDone 在主线程被调用。 */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                TokenStore.clear(getApplication())
                NetworkModule.cookieJar.clearAll()
            }.onFailure { Log.w(TAG, "logout cleanup failed", it) }
            onDone()
        }
    }

    private companion object {
        const val TAG = "ProfileVM"
    }
}
