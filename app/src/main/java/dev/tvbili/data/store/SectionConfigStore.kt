package dev.tvbili.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tvbili.ui.home.SectionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 用户选定的首页分区 ID + 顺序持久化。
 *
 * - 存储格式：逗号分隔的 enum name 字符串（"RCMD,HOT,LIVE"）。
 *   DataStore Preferences 没有 ordered List 类型；stringSetPreferencesKey 会丢顺序。
 * - 与 TokenStore 共享 `Context.dataStore`（"tv_bili_prefs"）。
 * - 默认值：[DEFAULT_SECTIONS] = [RCMD, HOT, LIVE]，与 PRD §"Phase 3 Scope" 默认值一致。
 * - 解析时丢弃未知 / 重复 enum name；保留首次出现顺序。
 * - 序列化 / 解析为纯函数，便于单元测试。
 */
object SectionConfigStore {

    val DEFAULT_SECTIONS: List<SectionId> =
        listOf(SectionId.RCMD, SectionId.HOT, SectionId.CINEMA, SectionId.LIVE)

    /**
     * Phase 4 之前的默认分区。检测到用户存的就是这套时，自动升级到 [DEFAULT_SECTIONS]
     * （在「热门」后插入「电影」），让没动过设置的老用户也能拿到新默认。
     * 用户一旦自定义过列表（哪怕只删一项），这个迁移就跳过——不会覆盖用户改动。
     */
    private val LEGACY_DEFAULT_SECTIONS: List<SectionId> =
        listOf(SectionId.RCMD, SectionId.HOT, SectionId.LIVE)

    @Volatile
    var current: List<SectionId> = DEFAULT_SECTIONS
        private set

    /** 启动期 `runBlocking { bootstrap() }`，让 HomeViewModel.init {} 第一帧直接拿到值。 */
    suspend fun bootstrap(context: Context) {
        val raw = context.tvBiliPrefs.data.first()[KEY]
        val parsed = parseSections(raw)
        val migrated = if (parsed == LEGACY_DEFAULT_SECTIONS) {
            Log.d(TAG, "migrate legacy default → new default (insert CINEMA after HOT)")
            DEFAULT_SECTIONS.also { saveBlocking(context, it) }
        } else parsed
        current = migrated ?: DEFAULT_SECTIONS
        Log.d(TAG, "bootstrap loaded ${current.map(SectionId::name)}")
    }

    private suspend fun saveBlocking(context: Context, sections: List<SectionId>) {
        context.tvBiliPrefs.edit { it[KEY] = serializeSections(sections) }
    }

    /** Compose 端：collectAsStateWithLifecycle 用。 */
    fun flow(context: Context): Flow<List<SectionId>> =
        context.tvBiliPrefs.data.map { prefs ->
            parseSections(prefs[KEY]) ?: DEFAULT_SECTIONS
        }

    suspend fun save(context: Context, sections: List<SectionId>) {
        val clean = sections.distinct().ifEmpty { DEFAULT_SECTIONS }
        val raw = serializeSections(clean)
        current = clean
        context.tvBiliPrefs.edit { it[KEY] = raw }
        Log.d(TAG, "saved ${clean.map(SectionId::name)}")
    }

    // === 纯函数（无 Context 依赖，便于测试） ===

    /** 序列化为逗号分隔 enum name 串。 */
    internal fun serializeSections(list: List<SectionId>): String =
        list.joinToString(",") { it.name }

    /**
     * 从逗号分隔串解析回 List<SectionId>。
     * - null/空串 → null（让调用方落到 DEFAULT_SECTIONS）
     * - 无效 name 静默丢弃
     * - 重复保留首次
     */
    internal fun parseSections(raw: String?): List<SectionId>? {
        if (raw.isNullOrBlank()) return null
        val names = SectionId.entries.associateBy { it.name }
        val out = mutableListOf<SectionId>()
        val seen = mutableSetOf<SectionId>()
        for (token in raw.split(',')) {
            val s = names[token.trim()] ?: continue
            if (seen.add(s)) out += s
        }
        return out.ifEmpty { null }
    }

    private val KEY = stringPreferencesKey("sections_order")
    private const val TAG = "SectionConfigStore"
}
