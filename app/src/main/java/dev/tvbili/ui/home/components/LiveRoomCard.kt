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

@Composable
fun LiveRoomCard(
    card: HomeCard.Live,
    onClick: () -> Unit,
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
            // 左上角 LIVE 角标
            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            // 右下角在线人数
            if (card.online > 0) {
                Text(
                    text = "${formatCountInt(card.online)}人",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append(card.uploader)
                if (card.areaName.isNotBlank()) {
                    append(" · ")
                    append(card.areaName)
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFFB0B0B0),
            fontSize = 12.sp,
        )
    }
}
