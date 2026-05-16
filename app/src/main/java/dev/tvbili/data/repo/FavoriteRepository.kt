package dev.tvbili.data.repo

import dev.tvbili.data.model.FavFolder
import dev.tvbili.data.model.toHomeCard
import dev.tvbili.data.store.TokenStore
import dev.tvbili.net.NetworkModule

/**
 * 用户收藏夹拉取。
 *
 * - [listFolders]：登录用户的全部收藏夹（默认 + 自建）；未登录返 failure
 * - [listFolderVideos]：单个收藏夹的视频分页；过滤掉 type ≠ 2 的非播放资源
 *
 * 所有方法返回 [Result]：code != 0 / 网络异常 / data null 都 failure。
 */
class FavoriteRepository {

    suspend fun listFolders(): Result<List<FavFolder>> = runCatching {
        val mid = TokenStore.mid
        require(mid > 0L) { "未登录" }
        val resp = NetworkModule.favoriteApi.getCreatedFolders(upMid = mid)
        require(resp.code == 0) { "fav folders code=${resp.code} msg=${resp.message}" }
        resp.data?.list.orEmpty()
    }

    /**
     * @param pageSize 默认 20；接口最大约 40
     * @return Pair<可播视频卡列表, hasMore>
     */
    suspend fun listFolderVideos(
        folderId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<Pair<List<HomeCard.Video>, Boolean>> = runCatching {
        val resp = NetworkModule.favoriteApi.getFolderResources(
            mediaId = folderId,
            pn = page,
            ps = pageSize,
        )
        require(resp.code == 0) { "fav resources code=${resp.code} msg=${resp.message}" }
        val data = resp.data ?: error("fav resources data null")
        val cards = data.medias.orEmpty()
            .filter { it.type == 2 && it.bvid.isNotBlank() }
            .map { it.toHomeCard() }
        cards to data.hasMore
    }
}
