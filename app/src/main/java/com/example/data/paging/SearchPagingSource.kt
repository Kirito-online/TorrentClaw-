package com.example.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.model.MediaItem
import com.example.data.model.SearchFilter
import com.example.data.repository.MediaRepository

class SearchPagingSource(
  private val repository: MediaRepository,
  private val filter: SearchFilter,
) : PagingSource<Int, MediaItem>() {

  private val seenIds = HashSet<String>()

  override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
    return state.anchorPosition?.let { anchor ->
      val page = state.closestPageToPosition(anchor)
      page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }
  }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
    val page = params.key ?: 1
    return try {
      val result = repository.searchPage(filter, page, params.loadSize.coerceAtMost(30))
      val deduped =
        result.mediaItems.filter { item ->
          val key = item.id.ifBlank { "${item.title}_${item.year}" }
          seenIds.add(key)
        }
      val nextKey = if (result.mediaItems.isEmpty() || result.mediaItems.size < 10) null else page + 1
      LoadResult.Page(
        data = deduped,
        prevKey = if (page == 1) null else page - 1,
        nextKey = nextKey,
      )
    } catch (e: Exception) {
      LoadResult.Error(e)
    }
  }
}
