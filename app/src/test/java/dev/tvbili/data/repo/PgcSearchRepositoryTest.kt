package dev.tvbili.data.repo

import dev.tvbili.data.api.SearchApi
import dev.tvbili.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PgcSearchRepositoryTest {
    private class FakeApi(val responses: List<HdPgcSearchResponse>) : SearchApi {
        val pages = mutableListOf<Int>()
        var error: Exception? = null
        override suspend fun searchAll(signed: Map<String, String>): SearchResponse = error("unexpected video search")
        override suspend fun searchHdPgc(signed: Map<String, String>): HdPgcSearchResponse {
            assertEquals("8", signed["type"])
            assertEquals("android_hd", signed["mobi_app"])
            error?.let { throw it }
            val page = requireNotNull(signed["pn"]).toInt()
            pages += page
            return responses[page - 1]
        }
    }

    private fun response(vararg cards: HdPgcSearchItem, pages: Int = 2) = HdPgcSearchResponse(
        data = HdPgcSearchData(items = cards.toList(), pages = pages),
    )
    private fun card(id: Long, category: Int) = HdPgcSearchItem(seasonId = id, seasonType = category, title = "节目$id")

    @Test fun `skip mixed pages until requested category is found`() = runTest {
        val api = FakeApi(listOf(response(card(1, 2)), response(card(2, 7), card(2, 7), card(3, 5))))
        val result = SearchRepository { api }.search("人生", SearchScope.VARIETY).getOrThrow()
        assertEquals(listOf(1, 2), api.pages)
        assertEquals(listOf("pgc_2"), result.cards.map { it.stableKey })
        assertNull(result.nextPage)
    }

    @Test fun `preserve server cursor when matching cards are returned early`() = runTest {
        val api = FakeApi(listOf(response(card(1, 2), card(2, 7))))
        val result = SearchRepository { api }.search("人生", SearchScope.CINEMA).getOrThrow()
        assertEquals(listOf("pgc_1"), result.cards.map { it.stableKey })
        assertEquals(2, result.nextPage)
        assertEquals(listOf(1), api.pages)
    }

    @Test fun `exhaust unrelated pages before reporting no results`() = runTest {
        val api = FakeApi(listOf(response(card(1, 2)), response(card(2, 3))))
        val result = SearchRepository { api }.search("人生", SearchScope.VARIETY).getOrThrow()
        assertTrue(result.cards.isEmpty())
        assertNull(result.nextPage)
        assertEquals(listOf(1, 2), api.pages)
    }

    @Test fun `API errors do not become empty search results`() = runTest {
        val api = FakeApi(listOf(HdPgcSearchResponse(code = -400, message = "请求错误")))
        assertTrue(SearchRepository { api }.search("电影", SearchScope.CINEMA).isFailure)
    }

    @Test fun `cancellation is propagated`() = runTest {
        val api = FakeApi(emptyList()).apply { error = CancellationException("cancelled") }
        try {
            SearchRepository { api }.search("电影", SearchScope.CINEMA)
            fail("cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
}
