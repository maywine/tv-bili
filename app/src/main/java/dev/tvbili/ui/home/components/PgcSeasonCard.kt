package dev.tvbili.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.tvbili.data.repo.HomeCard
import dev.tvbili.tv.tvFocusable

/**
 * 综艺/番剧/电影等 PGC 季度卡。
 *
 * - cover 用 2:3 竖图比例（PGC cover 在 B 站本来就是竖海报，比横封更贴合）。
 *   但首页 grid 整体用横向 16:9 卡—— Phase 4 设定的视觉一致性。为不破坏 grid，
 *   PGC 卡仍走 16:9，封面 `contentScale` Crop 居中裁切。
 * - [resolving] = true 时画半透明 spinner 蒙层，告诉用户「正在解析最新一集 → 跳转」。
 * - badge（会员 / 付费）显示在左上角红章；index_show 显示在右下角小角标。
 */
@Composable
fun PgcSeasonCard(
    card: HomeCard.PgcSeason,
    onClick: () -> Unit,
    resolving: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(cornerRadius = 12.dp, scaleOnFocus = 1.04f)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A)),
        ) {
            AsyncImage(
                model = card.coverUrl,
                contentDescription = card.title,
                modifier = Modifier.fillMaxSize(),
                filterQuality = FilterQuality.Low,
            )
            if (card.badge.isNotBlank()) {
                Text(
                    text = card.badge,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (card.indexShow.isNotBlank()) {
                Text(
                    text = card.indexShow,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (resolving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
    }
}
