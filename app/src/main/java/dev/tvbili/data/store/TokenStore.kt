package dev.tvbili.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 登录凭据 + 设备指纹的持久化存储。
 *
 * - 启动时调用 [bootstrap] 一次性 `first()` 读出全部值到 @Volatile 内存缓存，
 *   后续 OkHttp CookieJar 可同步读取，无 race condition。
 * - 写操作 [saveSession] / [clear] / [generateAndSaveBuvid3] 双更新内存 + DataStore。
 * - `buvid3` 首次启动自动生成（UUID + "infoc"），与 BiliPai 行为一致。
 */
object TokenStore {

    @Volatile
    var sessdata: String? = null
        private set

    @Volatile
    var biliJct: String? = null
        private set

    @Volatile
    var buvid3: String? = null
        private set

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    var mid: Long = 0L
        private set

    val isLoggedIn: Boolean get() = !sessdata.isNullOrEmpty()

    suspend fun bootstrap(context: Context) {
        val prefs = context.tvBiliPrefs.data.first()
        sessdata = prefs[SESSDATA_KEY]
        biliJct = prefs[BILI_JCT_KEY]
        accessToken = prefs[ACCESS_TOKEN_KEY]
        refreshToken = prefs[REFRESH_TOKEN_KEY]
        mid = prefs[MID_KEY] ?: 0L
        buvid3 = prefs[BUVID3_KEY] ?: generateAndSaveBuvid3(context)
    }

    suspend fun saveSession(
        context: Context,
        sessdata: String,
        biliJct: String,
        accessToken: String,
        refreshToken: String,
        mid: Long,
    ) {
        this.sessdata = sessdata
        this.biliJct = biliJct
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.mid = mid
        context.tvBiliPrefs.edit { p ->
            p[SESSDATA_KEY] = sessdata
            p[BILI_JCT_KEY] = biliJct
            p[ACCESS_TOKEN_KEY] = accessToken
            p[REFRESH_TOKEN_KEY] = refreshToken
            p[MID_KEY] = mid
        }
    }

    suspend fun clear(context: Context) {
        sessdata = null
        biliJct = null
        accessToken = null
        refreshToken = null
        mid = 0L
        context.tvBiliPrefs.edit { p ->
            p.remove(SESSDATA_KEY)
            p.remove(BILI_JCT_KEY)
            p.remove(ACCESS_TOKEN_KEY)
            p.remove(REFRESH_TOKEN_KEY)
            p.remove(MID_KEY)
        }
    }

    private suspend fun generateAndSaveBuvid3(context: Context): String {
        val v = UUID.randomUUID().toString().replace("-", "") + "infoc"
        buvid3 = v
        context.tvBiliPrefs.edit { it[BUVID3_KEY] = v }
        return v
    }

    private val SESSDATA_KEY = stringPreferencesKey("sessdata")
    private val BILI_JCT_KEY = stringPreferencesKey("bili_jct")
    private val BUVID3_KEY = stringPreferencesKey("buvid3")
    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val MID_KEY = longPreferencesKey("mid")
}

/**
 * WBI 签名 key 持久化（imgKey / subKey / 时间戳），24 小时缓存。
 * 由 [dev.tvbili.net.WbiKeyManager] 调用。
 */
object WbiKeysStore {

    private val IMG_KEY = stringPreferencesKey("wbi_img_key")
    private val SUB_KEY = stringPreferencesKey("wbi_sub_key")
    private val TS_KEY = longPreferencesKey("wbi_ts")

    /** 返回 (img, sub, savedAtMillis)；缺值时对应 null/0。 */
    suspend fun load(context: Context): Triple<String?, String?, Long> {
        val p = context.tvBiliPrefs.data.first()
        return Triple(p[IMG_KEY], p[SUB_KEY], p[TS_KEY] ?: 0L)
    }

    suspend fun save(context: Context, img: String, sub: String, savedAt: Long) {
        context.tvBiliPrefs.edit { p ->
            p[IMG_KEY] = img
            p[SUB_KEY] = sub
            p[TS_KEY] = savedAt
        }
    }
}
