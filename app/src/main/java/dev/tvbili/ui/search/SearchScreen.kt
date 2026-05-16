package dev.tvbili.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tvbili.ui.home.components.VideoCard

/**
 * 搜索屏：顶部 OutlinedTextField + IME → 下方结果网格。
 *
 * 焦点交互：
 * - 进屏 → LaunchedEffect 把焦点抓到 TextField，leanback ROM 自动弹 IME
 * - TextField 自带 focusable + 输入；**不要叠 `tvFocusable()`**，会和内置 indicator 冲突
 * - Back → BackHandler 收屏；系统先收 IME（这是 framework 默认行为）
 */
@Composable
fun SearchScreen(
    onNavigateToVideo: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = viewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val inputFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { inputFocus.requestFocus() }
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(inputFocus),
            singleLine = true,
            placeholder = { Text("搜视频…", color = Color(0xFF707070)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF404040),
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                SearchState.Idle -> Text(
                    text = "敲字搜视频",
                    color = Color(0xFF707070),
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )

                is SearchState.Empty -> Text(
                    text = "没找到「${s.keyword}」",
                    color = Color(0xFFB0B0B0),
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchState.Error -> Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                is SearchState.Loaded -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = s.cards, key = { it.stableKey }) { card ->
                        VideoCard(card = card, onClick = { onNavigateToVideo(card.bvid) })
                    }
                }
            }
        }
    }
}
