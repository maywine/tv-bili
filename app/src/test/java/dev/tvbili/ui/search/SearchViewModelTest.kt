package dev.tvbili.ui.search

import dev.tvbili.data.repo.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun card(id: Long, title: String = "节目$id") = HomeCard.PgcSeason(id, title, "", "", "")

    @Test fun `late response cannot overwrite newer query`() = runTest {
        val vm = SearchViewModel(SearchScope.VARIETY, search = { keyword, _, _ ->
            if (keyword == "old") withContext(NonCancellable) { delay(1000) }
            Result.success(SearchPage(listOf(card(1, keyword))))
        })
        vm.onQueryChange("old")
        advanceTimeBy(300)
        runCurrent()
        vm.onQueryChange("new")
        advanceUntilIdle()
        val state = vm.state.value as SearchState.Loaded
        assertEquals("new", state.keyword)
        assertEquals("new", state.cards.single().title)
    }

    @Test fun `submit avoids duplicate debounced request and supports retry`() = runTest {
        var requests = 0
        val vm = SearchViewModel(search = { _, _, _ -> requests++; Result.success(SearchPage(emptyList())) })
        vm.onQueryChange("test")
        vm.submit()
        advanceUntilIdle()
        assertEquals(1, requests)
        vm.submit()
        advanceUntilIdle()
        assertEquals(2, requests)
    }

    @Test fun `clearing query prevents stale results from returning`() = runTest {
        val vm = SearchViewModel(search = { _, _, _ ->
            withContext(NonCancellable) { delay(1000) }
            Result.success(SearchPage(listOf(card(1))))
        })
        vm.onQueryChange("test")
        advanceTimeBy(300)
        runCurrent()
        vm.onQueryChange(" ")
        advanceUntilIdle()
        assertEquals(SearchState.Idle, vm.state.value)
    }

    @Test fun `pagination deduplicates results and stops at last page`() = runTest {
        val requests = mutableListOf<Int>()
        val vm = SearchViewModel(SearchScope.CINEMA, search = { _, scope, page ->
            assertEquals(SearchScope.CINEMA, scope)
            requests += page
            Result.success(if (page == 1) SearchPage(listOf(card(1)), 2) else SearchPage(listOf(card(1), card(2))))
        })
        vm.onQueryChange("人生")
        advanceUntilIdle()
        vm.loadMore()
        vm.loadMore()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2), requests)
        assertEquals(listOf("pgc_1", "pgc_2"), (vm.state.value as SearchState.Loaded).cards.map { it.stableKey })
    }

    @Test fun `failed pagination preserves results and can retry the same page`() = runTest {
        var failed = false
        val vm = SearchViewModel(search = { _, _, page ->
            when {
                page == 1 -> Result.success(SearchPage(listOf(card(1)), 2))
                !failed -> { failed = true; Result.failure(IllegalStateException("offline")) }
                else -> Result.success(SearchPage(listOf(card(2))))
            }
        })
        vm.onQueryChange("test")
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        val failedState = vm.state.value as SearchState.Loaded
        assertEquals(1, failedState.cards.size)
        assertEquals("offline", failedState.appendError)
        vm.loadMore()
        advanceUntilIdle()
        val recovered = vm.state.value as SearchState.Loaded
        assertEquals(2, recovered.cards.size)
        assertNull(recovered.appendError)
    }

    @Test fun `movie and variety keep independent queries`() = runTest {
        val search: suspend (String, SearchScope, Int) -> Result<SearchPage> = { _, _, _ -> Result.success(SearchPage(emptyList())) }
        val movie = SearchViewModel(SearchScope.CINEMA, search)
        val variety = SearchViewModel(SearchScope.VARIETY, search)
        movie.onQueryChange("流浪地球")
        variety.onQueryChange("天赐的声音")
        advanceUntilIdle()
        assertEquals("流浪地球", movie.query.value)
        assertEquals("天赐的声音", variety.query.value)
    }
}
