// ItemsPage.kt

package com.mochyy.mochymusic.models

import com.music.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
