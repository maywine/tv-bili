package dev.tvbili.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tvbili.tv.tvFocusable
import dev.tvbili.ui.home.SectionId

@Composable
fun SectionsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SectionsSettingsViewModel = viewModel(),
) {
    val selected by vm.selected.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "分区管理",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )

        // 已选区
        Text("已选（${selected.size}）", color = Color(0xFFB0B0B0), fontSize = 14.sp)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = selected, key = { "sel_${it.name}" }) { sec ->
                SelectedRow(
                    section = sec,
                    canMoveUp = selected.indexOf(sec) > 0,
                    canMoveDown = selected.indexOf(sec) < selected.lastIndex,
                    onUp = { vm.moveUp(sec) },
                    onDown = { vm.moveDown(sec) },
                    onRemove = { vm.toggle(sec) },
                )
            }
        }

        Text("候选", color = Color(0xFFB0B0B0), fontSize = 14.sp)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = vm.available, key = { "avail_${it.name}" }) { sec ->
                AvailableRow(section = sec, onAdd = { vm.toggle(sec) })
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SaveButton(
                enabled = selected.isNotEmpty(),
                onClick = { vm.save(onDone = onBack) },
            )
        }
    }
}

@Composable
private fun SelectedRow(
    section: SectionId,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconCell(symbol = "↑", enabled = canMoveUp, onClick = onUp)
        IconCell(symbol = "↓", enabled = canMoveDown, onClick = onDown)
        Text(
            text = section.label,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        IconCell(symbol = "✕", enabled = true, onClick = onRemove)
    }
}

@Composable
private fun AvailableRow(
    section: SectionId,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101010))
            .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.02f)
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("＋", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        Text(
            text = section.label,
            color = Color(0xFFE0E0E0),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (section.kind == SectionId.Kind.PLACEHOLDER) {
            Text("（即将上线）", color = Color(0xFF707070), fontSize = 12.sp)
        }
    }
}

@Composable
private fun IconCell(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Color(0xFF2A2A2A) else Color(0xFF151515))
            .tvFocusable(cornerRadius = 20.dp, scaleOnFocus = 1.1f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = if (enabled) Color.White else Color(0xFF505050),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else Color(0xFF505050),
            )
            .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.06f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "保存并返回",
            color = if (enabled) Color.White else Color(0xFF707070),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
