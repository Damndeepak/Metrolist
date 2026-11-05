package com.mochyy.mochymusic.playback.custom

/**
 * Every provider that's on was asked for the song and none can play it. Asking again gives the
 * same answer, so playback moves on with this as the reason instead of retrying.
 */
class NoProviderException(reason: String) : java.io.IOException(reason)
