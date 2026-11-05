// SimilarRecommendation.kt

package com.mochyy.mochymusic.models

import com.music.innertube.models.YTItem
import com.mochyy.mochymusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
