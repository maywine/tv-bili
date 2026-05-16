package dev.tvbili.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// 全局唯一 DataStore 委托。所有 *Store 共用 tv_bili_prefs 文件。
// 多个 `by preferencesDataStore(...)` 即便 name 相同也会各建实例并指向同
// 一文件，运行时 OkioStorage 直接抛 IllegalStateException。
internal val Context.tvBiliPrefs: DataStore<Preferences>
    by preferencesDataStore(name = "tv_bili_prefs")
