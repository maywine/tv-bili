package dev.tvbili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import dev.tvbili.data.store.TokenStore
import dev.tvbili.tv.LocalIsTvDevice
import dev.tvbili.tv.TvUtils
import dev.tvbili.ui.favorite.FavoriteScreen
import dev.tvbili.ui.history.HistoryScreen
import dev.tvbili.ui.home.HomeScreen
import dev.tvbili.ui.live.LiveRoomScreen
import dev.tvbili.ui.login.LoginScreen
import dev.tvbili.ui.profile.ProfileScreen
import dev.tvbili.ui.search.SearchScreen
import dev.tvbili.ui.settings.SectionsSettingsScreen
import dev.tvbili.ui.theme.TvBiliTheme
import dev.tvbili.ui.video.VideoDetailScreen

private sealed interface AppScreen {
    data object Home : AppScreen
    data object Settings : AppScreen
    data object Search : AppScreen
    data object Profile : AppScreen
    data object History : AppScreen
    data object Favorite : AppScreen
    /**
     * @param origin 返回时回到的来源页（无真正回退栈时由它记住「从哪进来的」）。
     *   从历史/收藏/搜索点进视频，按返回应回到对应列表页，而非一律回首页。默认 [Home]。
     */
    data class Video(val bvid: String, val origin: AppScreen = Home) : AppScreen
    data class Live(val roomId: Long, val origin: AppScreen = Home) : AppScreen
}

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(
                LocalIsTvDevice provides TvUtils.isTv(this@MainActivity),
            ) {
                TvBiliTheme {
                    var loggedIn by remember { mutableStateOf(TokenStore.isLoggedIn) }
                    var screen: AppScreen by remember { mutableStateOf(AppScreen.Home) }

                    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                        when {
                            !loggedIn -> LoginScreen(
                                modifier = Modifier.padding(padding),
                                onLoggedIn = { loggedIn = true },
                            )
                            screen is AppScreen.Settings -> {
                                BackHandler { screen = AppScreen.Home }
                                SectionsSettingsScreen(
                                    modifier = Modifier.padding(padding),
                                    onBack = { screen = AppScreen.Home },
                                )
                            }
                            screen is AppScreen.Video -> {
                                val origin = (screen as AppScreen.Video).origin
                                BackHandler { screen = origin }
                                VideoDetailScreen(
                                    modifier = Modifier.padding(padding),
                                    bvid = (screen as AppScreen.Video).bvid,
                                    onBack = { screen = origin },
                                )
                            }
                            screen is AppScreen.Live -> {
                                val origin = (screen as AppScreen.Live).origin
                                BackHandler { screen = origin }
                                LiveRoomScreen(
                                    modifier = Modifier.padding(padding),
                                    roomId = (screen as AppScreen.Live).roomId,
                                    onBack = { screen = origin },
                                )
                            }
                            screen is AppScreen.Search -> {
                                BackHandler { screen = AppScreen.Home }
                                SearchScreen(
                                    modifier = Modifier.padding(padding),
                                    onNavigateToVideo = { bvid -> screen = AppScreen.Video(bvid, AppScreen.Search) },
                                    onBack = { screen = AppScreen.Home },
                                )
                            }
                            screen is AppScreen.Profile -> {
                                BackHandler { screen = AppScreen.Home }
                                ProfileScreen(
                                    modifier = Modifier.padding(padding),
                                    onBack = { screen = AppScreen.Home },
                                    onLoggedOut = {
                                        // TokenStore 已清；翻 loggedIn 后 Scaffold 自然路由回 LoginScreen
                                        screen = AppScreen.Home
                                        loggedIn = false
                                    },
                                    onNavigateToHistory = { screen = AppScreen.History },
                                    onNavigateToFavorite = { screen = AppScreen.Favorite },
                                )
                            }
                            screen is AppScreen.History -> {
                                BackHandler { screen = AppScreen.Profile }
                                HistoryScreen(
                                    modifier = Modifier.padding(padding),
                                    onBack = { screen = AppScreen.Profile },
                                    onNavigateToVideo = { bvid -> screen = AppScreen.Video(bvid, AppScreen.History) },
                                )
                            }
                            screen is AppScreen.Favorite -> {
                                BackHandler { screen = AppScreen.Profile }
                                FavoriteScreen(
                                    modifier = Modifier.padding(padding),
                                    onBack = { screen = AppScreen.Profile },
                                    onNavigateToVideo = { bvid -> screen = AppScreen.Video(bvid, AppScreen.Favorite) },
                                )
                            }
                            else -> HomeScreen(
                                modifier = Modifier.padding(padding),
                                onNavigateToSettings = { screen = AppScreen.Settings },
                                onNavigateToVideo = { bvid -> screen = AppScreen.Video(bvid) },
                                onNavigateToLive = { rid -> screen = AppScreen.Live(rid) },
                                onNavigateToSearch = { screen = AppScreen.Search },
                                onNavigateToProfile = { screen = AppScreen.Profile },
                            )
                        }
                    }
                }
            }
        }
    }
}
