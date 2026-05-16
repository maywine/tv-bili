package dev.tvbili.data.store

import dev.tvbili.ui.home.SectionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionConfigStoreTest {

    @Test
    fun `serialize roundtrip preserves order`() {
        val input = listOf(SectionId.LIVE, SectionId.RANKING, SectionId.RCMD)
        val raw = SectionConfigStore.serializeSections(input)
        assertEquals("LIVE,RANKING,RCMD", raw)
        assertEquals(input, SectionConfigStore.parseSections(raw))
    }

    @Test
    fun `parse null returns null`() {
        assertNull(SectionConfigStore.parseSections(null))
    }

    @Test
    fun `parse blank returns null`() {
        assertNull(SectionConfigStore.parseSections("   "))
    }

    @Test
    fun `parse drops unknown names`() {
        val raw = "RCMD,UNKNOWN_TOKEN,HOT"
        assertEquals(
            listOf(SectionId.RCMD, SectionId.HOT),
            SectionConfigStore.parseSections(raw),
        )
    }

    @Test
    fun `parse dedupes keeping first occurrence`() {
        val raw = "RCMD,HOT,RCMD,LIVE"
        assertEquals(
            listOf(SectionId.RCMD, SectionId.HOT, SectionId.LIVE),
            SectionConfigStore.parseSections(raw),
        )
    }

    @Test
    fun `parse tolerates surrounding whitespace`() {
        val raw = " RCMD , HOT "
        assertEquals(
            listOf(SectionId.RCMD, SectionId.HOT),
            SectionConfigStore.parseSections(raw),
        )
    }

    @Test
    fun `parse all unknown returns null`() {
        assertNull(SectionConfigStore.parseSections("FOO,BAR"))
    }

    @Test
    fun `DEFAULT_SECTIONS matches current PRD spec`() {
        // Phase 5 起：默认在「热门」后插入「电影」
        assertEquals(
            listOf(SectionId.RCMD, SectionId.HOT, SectionId.CINEMA, SectionId.LIVE),
            SectionConfigStore.DEFAULT_SECTIONS,
        )
    }
}
