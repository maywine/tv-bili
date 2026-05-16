package dev.tvbili.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.store.SectionConfigStore
import dev.tvbili.ui.home.SectionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings 草稿态：进入屏幕时从 [SectionConfigStore.current] 复制一份，
 * 编辑过程不写 DataStore，[save] 才一次性持久化。
 *
 * 保留顺序：[selected] 是 List<SectionId>；[available] 是「未选中候选」的
 * 派生列表（按 enum 声明顺序），不存 State。
 */
class SectionsSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _selected = MutableStateFlow(SectionConfigStore.current)
    val selected: StateFlow<List<SectionId>> = _selected.asStateFlow()

    val available: List<SectionId>
        get() = SectionId.entries.filterNot { it in _selected.value }

    fun toggle(section: SectionId) {
        _selected.value = _selected.value.toMutableList().apply {
            if (contains(section)) remove(section) else add(section)
        }
    }

    fun moveUp(section: SectionId) {
        val list = _selected.value.toMutableList()
        val i = list.indexOf(section)
        if (i > 0) {
            list[i] = list[i - 1]
            list[i - 1] = section
            _selected.value = list
        }
    }

    fun moveDown(section: SectionId) {
        val list = _selected.value.toMutableList()
        val i = list.indexOf(section)
        if (i in 0 until list.lastIndex) {
            list[i] = list[i + 1]
            list[i + 1] = section
            _selected.value = list
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            SectionConfigStore.save(getApplication(), _selected.value)
            Log.d(TAG, "saved ${_selected.value.map(SectionId::name)}")
            onDone()
        }
    }

    private companion object {
        const val TAG = "SectionsSettingsVM"
    }
}
