package com.metrolist.music.lyrics

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.R
import com.metrolist.music.constants.FloatingLyricsKey
import com.metrolist.music.constants.VerciEmojiKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

/** The existing media foreground service owns this optional overlay and its cleanup. */
internal class FloatingLyricsController(
    private val context: Context,
    parent: CoroutineScope,
    database: MusicDatabase,
    players: StateFlow<ExoPlayer?>,
    private val energy: () -> Float,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob(parent.coroutineContext[Job]))
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var enabled = false
    private var emojis = true
    private var advanced = false
    private var player: ExoPlayer? = null
    private var words: List<VerciWord> = emptyList()
    private var offset = 0L
    private val track = MutableStateFlow<String?>(null)
    private val requested = MutableStateFlow(false)
    private var expanded = false
    private var attached = false
    private val density = context.resources.displayMetrics.density
    private val view = BubbleView()
    private val params = WindowManager.LayoutParams(
        popupWidth(), dp(76), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = (context.resources.displayMetrics.widthPixels - width) / 2
        y = dp(48)
        preferredRefreshRate = 120f
    }
    private val observer = LifecycleEventObserver { _, _ -> refresh() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) {
            track.value = p.currentMediaItem?.mediaId
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) view.clock.reset()
            refresh()
            view.invalidate()
        }
    }
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            context.dataStore.data.map { Triple(it[FloatingLyricsKey] ?: false, it[VerciEmojiKey] ?: true, it[com.metrolist.music.constants.VerciAdvancedEmojiKey] ?: false) }.distinctUntilChanged().collect {
                enabled = it.first; requested.value = enabled; emojis = it.second; advanced = it.third
                words = words.map { word -> word.copy(emoji = verciEmoji(word.text, advanced, emojis)) }; refresh()
            }
        }
        scope.launch {
            players.collectLatest { current ->
                player?.removeListener(listener)
                player = current
                current?.addListener(listener)
                track.value = current?.currentMediaItem?.mediaId
                view.clock.reset(); refresh()
            }
        }
        scope.launch {
            combine(track, requested) { id, on -> if (on) id else null }.distinctUntilChanged().collectLatest { id ->
                words = emptyList(); offset = 0; view.clock.reset(); view.invalidate()
                if (id != null) combine(database.lyrics(id), database.song(id)) { lyrics, song -> Pair(lyrics, song) }.collectLatest { (lyrics, song) ->
                    val parsed = withContext(Dispatchers.Default) { buildVerciWords(LyricsUtils.parseLyrics(lyrics?.lyrics.orEmpty())) }
                    words = parsed.map { word -> word.copy(emoji = verciEmoji(word.text, advanced, emojis)) }
                    offset = song?.song?.lyricsOffset?.toLong() ?: 0
                    view.invalidate()
                }
            }
        }
    }
    private fun dp(value: Int) = (value * density).toInt()
    private fun popupWidth() = min(dp(340), context.resources.displayMetrics.widthPixels - dp(24))
    private fun refresh() {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val visible = enabled && Settings.canDrawOverlays(context) && !foreground && power.isInteractive && !keyguard.isKeyguardLocked && player?.currentMediaItem != null
        if (visible && !attached) {
            attached = runCatching { windows.addView(view, params); true }.getOrDefault(false)
        } else if (!visible && attached) hide()
        if (attached) view.invalidate()
    }
    private fun hide() { if (attached) { runCatching { windows.removeViewImmediate(view) }; attached = false } }
    fun close() {
        hide(); player?.removeListener(listener)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        runCatching { context.unregisterReceiver(receiver) }
        scope.cancel()
    }
    private fun disable() {
        enabled = false; hide()
        scope.launch { context.dataStore.edit { it[FloatingLyricsKey] = false } }
    }
    private inner class BubbleView : View(context) {
        val clock = VerciPlaybackClock()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastWord = ""
        private var previousWord = ""
        private var changed = 0L
        private var downX = 0f
        private var downY = 0f
        private var originX = 0
        private var originY = 0
        private var moved = false
        init { isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val p = player ?: return
            val now = System.nanoTime()
            val position = if (p.isPlaying) clock.position(p.currentPosition, now, p.playbackParameters.speed) else p.currentPosition
            val index = verciWordIndex(words, position + offset)
            val active = words.getOrNull(index)
            val word = active?.let { it.text + if (it.emoji.isNotEmpty()) " ${it.emoji}" else "" } ?: p.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            if (word != lastWord) {
                previousWord = lastWord; lastWord = word; changed = now
                contentDescription = word + ". " + context.getString(R.string.verci_bubble_accessibility)
            }
            paint.color = Color.rgb(17, 15, 23); paint.alpha = 248; paint.style = Paint.Style.FILL
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(30).toFloat(), dp(30).toFloat(), paint)
            paint.color = Color.rgb(194, 161, 255); paint.alpha = (100 + 100 * energy()).toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = density * 1.5f
            canvas.drawRoundRect(density, density, width - density, height - density, dp(30).toFloat(), dp(30).toFloat(), paint)
            paint.style = Paint.Style.FILL; paint.alpha = 255; paint.color = Color.WHITE; paint.typeface = Typeface.DEFAULT_BOLD
            val progress = ((now - changed) / 220_000_000f).coerceIn(0f, 1f)
            val ease = 1 - (1 - progress) * (1 - progress) * (1 - progress)
            val y = if (expanded) height * .46f else height * .58f
            fun drawWord(value: String, alpha: Float, shift: Float) {
                paint.textSize = density * if (expanded) 34 else 24
                val measured = paint.measureText(value)
                if (measured > width - dp(68)) paint.textSize *= (width - dp(68)) / measured
                paint.alpha = (255 * alpha).toInt()
                canvas.drawText(value, (width - paint.measureText(value)) / 2, y + shift, paint)
            }
            if (ease < 1) drawWord(previousWord, 1 - ease, -dp(12) * ease)
            drawWord(word, ease, dp(12) * (1 - ease))
            paint.textSize = dp(23).toFloat(); paint.alpha = 220
            canvas.drawText("×", width - dp(31).toFloat(), dp(29).toFloat(), paint)
            if (expanded) {
                paint.textSize = dp(12).toFloat(); paint.alpha = 155
                val title = p.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty().take(34)
                canvas.drawText(title, dp(20).toFloat(), dp(31).toFloat(), paint)
                val duration = p.duration.coerceAtLeast(1)
                val start = dp(24).toFloat(); val end = width - dp(24).toFloat(); val lineY = height - dp(65).toFloat()
                paint.strokeWidth = dp(3).toFloat(); paint.alpha = 70
                canvas.drawLine(start, lineY, end, lineY, paint)
                paint.alpha = 240
                canvas.drawLine(start, lineY, start + (end - start) * (position.toFloat() / duration).coerceIn(0f, 1f), lineY, paint)
                paint.textSize = dp(24).toFloat()
                val labels = listOf("‹", if (p.isPlaying) "Ⅱ" else "▶", "›")
                labels.forEachIndexed { i, text -> canvas.drawText(text, width * (i + 1) / 4f - paint.measureText(text) / 2, height - dp(21).toFloat(), paint) }
            }
            if (attached && power.isInteractive && !keyguard.isKeyguardLocked && (p.isPlaying || ease < 1)) postInvalidateOnAnimation()
        }
        override fun performClick(): Boolean {
            super.performClick()
            expanded = !expanded; params.height = dp(if (expanded) 214 else 76)
            params.y = params.y.coerceAtMost((context.resources.displayMetrics.heightPixels - params.height).coerceAtLeast(dp(24)))
            if (attached) runCatching { windows.updateViewLayout(this, params) }
            invalidate(); return true
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y; moved = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > dp(10)) moved = true
                    if (moved) {
                        params.x = (originX + dx).toInt().coerceIn(0, (context.resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0))
                        params.y = (originY + dy).toInt().coerceIn(dp(24), (context.resources.displayMetrics.heightPixels - params.height).coerceAtLeast(dp(24)))
                        if (attached) runCatching { windows.updateViewLayout(this, params) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) when {
                        event.x > width - dp(48) && event.y < dp(42) -> disable()
                        expanded && event.y > height - dp(52) -> when {
                            event.x < width / 3 -> player?.seekToPreviousMediaItem()
                            event.x > width * 2 / 3 -> player?.seekToNextMediaItem()
                            else -> player?.let { if (it.isPlaying) it.pause() else it.play() }
                        }
                        expanded && event.y > height - dp(88) -> player?.let { p -> if (p.duration > 0) p.seekTo((p.duration * ((event.x - dp(24)) / (width - dp(48))).coerceIn(0f, 1f)).toLong()) }
                        else -> performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(event)
        }
    }
}
