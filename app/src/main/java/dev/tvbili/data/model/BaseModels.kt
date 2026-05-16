package dev.tvbili.data.model

import kotlinx.serialization.Serializable

/**
 * 通用 B 站接口响应包装。code != 0 时 data 可能为空。
 * Phase 1 不强制使用——具体 Login/Nav 模型自定义；Phase 2+ 推荐流/热门等沿用。
 */
@Serializable
data class BiliResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: T? = null,
)
