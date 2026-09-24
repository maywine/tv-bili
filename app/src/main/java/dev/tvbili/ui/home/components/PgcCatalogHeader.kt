package dev.tvbili.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tvbili.data.repo.PgcOrder
import dev.tvbili.tv.tvFocusable

@Composable
fun PgcCatalogHeader(label: String, order: PgcOrder, onOrder: (PgcOrder) -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PgcOrder.entries.forEach { option ->
            Text(
                option.label,
                color = Color.White,
                modifier = Modifier
                    .tvFocusable(cornerRadius = 8.dp)
                    .clickable { onOrder(option) }
                    .background(
                        if (order == option) MaterialTheme.colorScheme.primary else Color(0xFF242424),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "搜索$label",
            color = Color.White,
            modifier = Modifier.tvFocusable(cornerRadius = 8.dp)
                .clickable(onClick = onSearch)
                .background(Color(0xFF242424), RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
