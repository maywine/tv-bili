package dev.tvbili.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LoginViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startLogin() }
    LaunchedEffect(state) {
        if (state is LoginState.Success) onLoggedIn()
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "tv-bili 扫码登录",
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )

            when (val s = state) {
                LoginState.Idle, LoginState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is LoginState.QrReady -> {
                    QrBlock(bitmap = s.bitmap, hint = "请用手机 B 站 App 扫码")
                }
                is LoginState.Scanned -> {
                    QrBlock(bitmap = s.bitmap, hint = "已扫码，请在手机上确认", dim = true)
                }
                LoginState.Success -> {
                    Text("登录成功，正在进入...", color = Color.White, fontSize = 20.sp)
                }
                is LoginState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                        Text("3 秒后自动重试", color = Color.LightGray, fontSize = 14.sp)
                        LaunchedEffect(s) {
                            delay(3000)
                            vm.retry()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrBlock(
    bitmap: android.graphics.Bitmap,
    hint: String,
    dim: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Login QR code",
                modifier = Modifier.size(300.dp),
            )
            if (dim) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }
        Text(hint, color = Color.White, fontSize = 18.sp)
    }
}
