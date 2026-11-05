package com.mochyy.mochymusic.deeplist

import com.mochyy.mochymusic.db.LegacyDeeplistImport
import kotlinx.coroutines.flow.first

internal suspend fun awaitDeeplistLibrary(): Boolean {
    val state = LegacyDeeplistImport.state.first { it.completed || it.error != null }
    return state.completed && state.error == null
}
