package com.mochyy.mochymusic.deeplist

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal val DeeplistSyncEnabledKey = booleanPreferencesKey("deeplistSyncEnabled")
internal val DeeplistWebsiteKey = stringPreferencesKey("deeplistWebsite")
internal val DeeplistTokenKey = stringPreferencesKey("deeplistToken")
