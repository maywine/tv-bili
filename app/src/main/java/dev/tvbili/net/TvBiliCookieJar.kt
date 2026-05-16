package dev.tvbili.net

import dev.tvbili.data.store.TokenStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp CookieJar：
 * - 响应里的 Set-Cookie 暂存到 host bucket（仅做 fallback；登录成功的关键 cookie
 *   由 [dev.tvbili.ui.login.LoginViewModel] 主动落 [TokenStore]）
 * - 请求时从 [TokenStore] 内存缓存读 SESSDATA / bili_jct / buvid3，注入到
 *   `bilibili.com` 域请求
 */
class TvBiliCookieJar : CookieJar {

    private val lock = Any()
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val bucket = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { newCookie ->
                bucket.removeAll { it.name == newCookie.name }
                bucket.add(newCookie)
            }
        }
    }

    /** 退出登录时清空 host bucket；TokenStore.clear() 已切断 SESSDATA/bili_jct 注入。 */
    fun clearAll() {
        synchronized(lock) { store.clear() }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = mutableListOf<Cookie>()

        synchronized(lock) {
            store[url.host]?.let { cookies.addAll(it) }
        }

        val biliDomain = if (url.host.endsWith("bilibili.com")) "bilibili.com" else url.host

        TokenStore.buvid3?.takeIf { it.isNotEmpty() }?.let { v ->
            if (cookies.none { it.name == "buvid3" }) {
                cookies.add(
                    Cookie.Builder()
                        .domain(url.host)
                        .name("buvid3")
                        .value(v)
                        .build(),
                )
            }
        }

        TokenStore.sessdata?.takeIf { it.isNotEmpty() }?.let { v ->
            cookies.removeAll { it.name == "SESSDATA" }
            cookies.add(
                Cookie.Builder()
                    .domain(biliDomain)
                    .name("SESSDATA")
                    .value(v)
                    .build(),
            )
        }

        TokenStore.biliJct?.takeIf { it.isNotEmpty() }?.let { v ->
            cookies.removeAll { it.name == "bili_jct" }
            cookies.add(
                Cookie.Builder()
                    .domain(biliDomain)
                    .name("bili_jct")
                    .value(v)
                    .build(),
            )
        }

        return cookies
    }
}
