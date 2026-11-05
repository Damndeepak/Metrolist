package com.music.innertube

/** what a request fails with while offline mode is on, an IOException so callers that treat a failed connection as nothing to show carry on doing so */
class OfflineModeException : java.io.IOException("Offline mode is on")
