package dev.tvbili.data.model

import dev.tvbili.data.repo.HomeCard
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvPgcIndexResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvPgcIndexData? = null,
)

@Serializable
data class TvPgcIndexData(
    val result: List<TvPgcCard> = emptyList(),
    val num: Int = 1,
    val size: Int = 20,
    val total: Int = 0,
) {
    fun hasNext(requestedPage: Int): Boolean =
        result.isNotEmpty() && requestedPage.toLong() * size.coerceAtLeast(1) < total
}

@Serializable
data class TvPgcSearchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvPgcSearchData? = null,
)

@Serializable
data class TvPgcSearchData(
    @SerialName("result_v2") val modules: List<TvSearchModule> = emptyList(),
    @SerialName("pageinfo") val pageInfo: TvSearchPageInfo? = null,
)

@Serializable
data class TvSearchModule(val list: List<TvPgcCard> = emptyList())

@Serializable
data class TvSearchPageInfo(val tvpgc: TvSearchPage? = null)

@Serializable
data class TvSearchPage(val pages: Int = 0)

@Serializable
data class TvPgcCard(
    @SerialName("card_type") val cardType: Int = 0,
    @SerialName("card_id") val seasonId: Long = 0,
    val title: String = "",
    @SerialName("horizontal_url") val horizontalCover: String = "",
    @SerialName("vertical_url") val verticalCover: String = "",
    val subtitle2: String = "",
    val catalog: TvPgcCatalog? = null,
    val cornermark: TvPgcBadge? = null,
    @SerialName("pgc_ext") val pgc: TvPgcExtra? = null,
)

@Serializable
data class TvPgcCatalog(@SerialName("catalog_id") val id: Int = 0)

@Serializable
data class TvPgcBadge(val text: String = "")

@Serializable
data class TvPgcExtra(@SerialName("show_index") val indexShow: String = "")

/** TV 搜索的 category 参数不会可靠过滤类型，必须检查每张卡的 catalog_id。 */
internal fun List<TvPgcCard>.toPgcCards(seasonType: Int): List<HomeCard.PgcSeason> =
    asSequence()
        .filter { it.cardType == 2 && it.catalog?.id == seasonType && it.seasonId > 0 && it.title.isNotBlank() }
        .distinctBy { it.seasonId }
        .map {
            HomeCard.PgcSeason(
                seasonId = it.seasonId,
                title = cleanSearchHtml(it.title),
                coverUrl = normalizeCoverUrl(it.horizontalCover.ifBlank { it.verticalCover }),
                indexShow = it.pgc?.indexShow?.takeIf(String::isNotBlank) ?: it.subtitle2,
                badge = it.cornermark?.text.orEmpty(),
            )
        }
        .toList()
