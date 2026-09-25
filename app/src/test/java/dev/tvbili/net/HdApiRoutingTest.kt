package dev.tvbili.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.tvbili.data.api.PgcApi
import dev.tvbili.data.api.SearchApi
import dev.tvbili.data.model.PgcPlayback
import dev.tvbili.data.model.VideoDetail
import dev.tvbili.data.model.decodeHdPlayUrl
import dev.tvbili.data.repo.PgcOrder
import dev.tvbili.data.repo.PgcRepository
import dev.tvbili.data.repo.PgcSource
import dev.tvbili.data.repo.SearchRepository
import dev.tvbili.data.repo.SearchScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class HdApiRoutingTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(HttpHeadersInterceptor()).addInterceptor(HdHeadersInterceptor()).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Test fun `catalog uses HD parameters and keeps source when paging`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"has_next":1,"list":[{"season_id":239101,"title":"天赐的声音7"}]}}"""))
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"has_next":0,"list":[]}}"""))
            val api = retrofit(server).create(PgcApi::class.java)
            val repository = PgcRepository { api }
            val first = repository.loadSeasonIndex(7, order = PgcOrder.UPDATED).getOrThrow()
            assertEquals(PgcSource.HD, first.source)
            assertTrue(first.hasMore)
            assertFalse(repository.loadSeasonIndex(7, 2, PgcOrder.UPDATED, first.source).getOrThrow().hasMore)
            val request = server.takeRequest()
            assertEquals("/pgc/season/index/result", request.requestUrl!!.encodedPath)
            assertEquals("android_hd", request.requestUrl!!.queryParameter("mobi_app"))
            assertEquals(AppSignUtils.HD_APP_KEY, request.requestUrl!!.queryParameter("appkey"))
            assertEquals("7", request.requestUrl!!.queryParameter("season_type"))
            assertEquals("0", request.requestUrl!!.queryParameter("order"))
            assertEquals("1", request.requestUrl!!.queryParameter("page"))
            assertNull(request.getHeader("Referer"))
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
        }
    }

    @Test fun `search uses app film endpoint and keeps category filtering`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"pages":1,"items":[{"season_id":1,"season_type":2,"title":"电影"},{"season_id":2,"season_type":7,"title":"综艺"}]}}"""))
            val api = retrofit(server).create(SearchApi::class.java)
            val result = SearchRepository { api }.search("测试", SearchScope.VARIETY).getOrThrow()
            assertEquals(listOf("pgc_2"), result.cards.map { it.stableKey })
            val request = server.takeRequest()
            assertEquals("/x/v2/search/type", request.requestUrl!!.encodedPath)
            assertEquals("8", request.requestUrl!!.queryParameter("type"))
            assertEquals("测试", request.requestUrl!!.queryParameter("keyword"))
            assertEquals("Mozilla/5.0 BiliDroid/2.0.1", request.getHeader("User-Agent"))
        }
    }

    @Test fun `episode resolution uses app detail module`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"season_id":10,"title":"节目","modules":[{"style":"positive","data":{"episodes":[{"id":20,"cid":30,"bvid":"BVepisode","duration":2000}]}}]}}"""))
            val api = retrofit(server).create(PgcApi::class.java)
            val result = PgcRepository { api }.resolveLatestEpisode(10).getOrThrow()
            assertEquals(20L, result.episodeId)
            assertEquals(30L, result.detail.cid)
            val request = server.takeRequest()
            assertEquals("/pgc/view/v2/app/season", request.requestUrl!!.encodedPath)
            assertEquals("10", request.requestUrl!!.queryParameter("season_id"))
        }
    }

    @Test fun `mobile playback accepts full response without forcing preview`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"result":"suee","quality":80,"is_preview":0,"durl":[{"url":"https://example.com/full.mp4"}] }"""))
            val api = retrofit(server).create(PgcApi::class.java)
            val target = PgcPlayback(10, 20, VideoDetail(bvid = "BVepisode", cid = 30))
            val params = hdPlaybackParams(target, 80, "hd-token", AppSignUtils.HD_APP_KEY, 1700000000)
            val result = decodeHdPlayUrl(api.getHdPlayUrl(params))
            assertEquals(0, result.isPreview)
            assertEquals("https://example.com/full.mp4", result.durl.single().validUrl())
            val request = server.takeRequest()
            assertEquals("/pgc/player/api/playurl", request.requestUrl!!.encodedPath)
            assertEquals("20", request.requestUrl!!.queryParameter("ep_id"))
            assertEquals("hd-token", request.requestUrl!!.queryParameter("access_key"))
            assertNull(request.requestUrl!!.queryParameter("playurl_type"))
        }
    }
}
