package dev.tvbili.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TvPgcModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun `decode TV index and keep only valid unique seasons in requested category`() {
        val response = json.decodeFromString<TvPgcIndexResponse>("""
            {"code":0,"data":{"num":1,"size":20,"total":539,"result":[
              {"card_type":2,"card_id":239101,"title":"天赐的声音 第7季",
               "catalog":{"catalog_id":7},"horizontal_url":"http://example.com/landscape.jpg",
               "vertical_url":"https://example.com/poster.jpg","pgc_ext":{"show_index":"更新至第12期"},
               "cornermark":{"text":"会员"},"unused":"ignored"},
              {"card_type":2,"card_id":239101,"title":"重复","catalog":{"catalog_id":7}},
              {"card_type":2,"card_id":44847,"title":"电影","catalog":{"catalog_id":2}},
              {"card_type":1,"card_id":99,"title":"剪辑","catalog":{"catalog_id":7}},
              {"card_type":2,"card_id":0,"title":"缺少ID","catalog":{"catalog_id":7}}
            ]}}
        """)
        val data = requireNotNull(response.data)
        val card = data.result.toPgcCards(7).single()
        assertEquals(239101L, card.seasonId)
        assertEquals("https://example.com/landscape.jpg", card.coverUrl)
        assertEquals("更新至第12期", card.indexShow)
        assertEquals("会员", card.badge)
        assertTrue(data.hasNext(1))
        assertFalse(data.hasNext(27))
        assertFalse(data.copy(result = emptyList()).hasNext(1))
    }

    @Test fun `decode nullable search modules and strip title highlighting`() {
        val response = json.decodeFromString<TvPgcSearchResponse>("""
            {"code":0,"data":{"result_v2":[{"list":null},{"list":[
              {"card_type":2,"card_id":95313,"title":"<em>天赐</em>的声音 &amp; 音乐",
               "catalog":{"catalog_id":7},"vertical_url":"//example.com/poster.jpg",
               "subtitle2":"全12期","pgc_ext":null,"cornermark":null}
            ]}],"pageinfo":{"tvpgc":{"pages":2,"total":25},"tvugc":null}}}
        """)
        val data = requireNotNull(response.data)
        val card = data.modules.flatMap { it.list }.toPgcCards(7).single()
        assertEquals("天赐的声音 & 音乐", card.title)
        assertEquals("https://example.com/poster.jpg", card.coverUrl)
        assertEquals("全12期", card.indexShow)
        assertEquals("", card.badge)
        assertEquals(2, data.pageInfo?.tvpgc?.pages)
    }
}
