package dev.tvbili.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.tvbili.data.model.NavData
import dev.tvbili.tv.LocalIsTvDevice
import dev.tvbili.tv.tvFocusable

/**
 * 个人中心。
 *
 * 设计要点：
 * - 仅含「头像 + 昵称 + 等级/VIP/硬币」展示 + 退出登录两步确认；登录是 OAuth 扫码，
 *   B 站这边没「修改资料」入口。
 * - 退出登录用两步确认（按一次显出取消/确认行，焦点默认放在「取消」上）——避免遥控器
 *   误触；TokenStore.clear 后 [onLoggedOut] 把 MainActivity 的 loggedIn 翻 false，
 *   状态机自然回到 LoginScreen。
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text(
            text = "个人中心",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )

        when (val s = state) {
            ProfileState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            is ProfileState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is ProfileState.Ready -> ProfileBody(
                data = s.data,
                onCancel = onBack,
                onConfirmLogout = { vm.logout(onLoggedOut) },
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToFavorite = onNavigateToFavorite,
            )
        }
    }
}

@Composable
private fun ProfileBody(
    data: NavData?,
    onCancel: () -> Unit,
    onConfirmLogout: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToFavorite: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val isTv = LocalIsTvDevice.current
    val cancelRequester = remember { FocusRequester() }

    // 进入确认态时把焦点钉在「取消」上——避免遥控器顺手按 OK 误登出
    LaunchedEffect(confirming) {
        if (confirming && isTv) {
            try { cancelRequester.requestFocus() } catch (_: Throwable) {}
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            val face = data?.face.orEmpty()
            if (face.isNotEmpty()) {
                AsyncImage(
                    model = face,
                    contentDescription = "avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text("👤", color = Color.White, fontSize = 48.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = data?.uname?.takeIf { it.isNotEmpty() } ?: "未知用户",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val mid = data?.mid ?: 0L
            if (mid > 0) {
                Text("UID: $mid", color = Color(0xFFB0B0B0), fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                data?.level_info?.current_level?.takeIf { it > 0 }?.let { lvl ->
                    Chip(text = "LV $lvl")
                }
                if ((data?.vipStatus ?: 0) > 0) {
                    Chip(text = "大会员", tint = Color(0xFFFB7299))
                }
                val coins = data?.money ?: 0.0
                if (coins > 0.0) {
                    Chip(text = "硬币 ${formatCoins(coins)}")
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    if (!confirming) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionButton(
                label = "历史记录",
                tint = Color(0xFF2A2A2A),
                onClick = onNavigateToHistory,
            )
            ActionButton(
                label = "我的收藏",
                tint = Color(0xFF2A2A2A),
                onClick = onNavigateToFavorite,
            )
        }
        Spacer(Modifier.height(8.dp))
        ActionButton(
            label = "退出登录",
            tint = MaterialTheme.colorScheme.error,
            onClick = { confirming = true },
        )
    } else {
        Text(
            text = "确定要退出当前账号吗？",
            color = Color(0xFFE0E0E0),
            fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionButton(
                label = "取消",
                tint = Color(0xFF505050),
                onClick = { confirming = false },
                modifier = Modifier.focusRequester(cancelRequester),
            )
            ActionButton(
                label = "确认退出",
                tint = MaterialTheme.colorScheme.error,
                onClick = onConfirmLogout,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = "返回键回到首页",
        color = Color(0xFF707070),
        fontSize = 12.sp,
    )
}

@Composable
private fun Chip(text: String, tint: Color = Color(0xFF2A2A2A)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(180.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.06f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatCoins(c: Double): String =
    if (c == c.toLong().toDouble()) c.toLong().toString() else "%.1f".format(c)
