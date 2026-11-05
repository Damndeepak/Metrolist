// ProfileMorph.kt
//
// The profile page opening out of the ID card on the Mochy page. The Mochy page zooms towards
// you and fades while the profile page's dotted black comes up behind it, and copies of the card's
// photo, name and card number fly to the spots the profile page keeps for them. When they land the
// page's own ones take over and the rest of the page rises in, one row after another. Back plays it
// the other way, into the card again.
//
// Everything is measured live from the real layouts on both ends, so a scrolled page, a long name
// or a tilted card still lines up on the first and last frame.

package com.mochyy.mochymusic.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.ui.component.HomeContentInset
import com.mochyy.mochymusic.ui.screens.recap.Emphasized
import com.mochyy.mochymusic.ui.screens.recap.lerpF
import com.mochyy.mochymusic.ui.screens.recap.recapDots
import com.mochyy.mochymusic.ui.screens.setup.onboarding.IdCardAnchors
import com.mochyy.mochymusic.ui.screens.setup.onboarding.idCardName
import com.mochyy.mochymusic.viewmodels.UserProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow

internal enum class ProfilePhase { Closed, Preparing, Opening, Open, Closing }

/** How much the Mochy page grows as it fades away behind the profile page. */
private const val PageZoom = 0.14f

/** The longest the page may take to lay out before the flight starts anyway. */
private const val PrepareTimeoutMs = 450L

// the rows rise in one after another; cards inside a row ripple in a touch after their row
private const val RevealRowStep = 0.08f
private const val RevealCardStep = 0.03f
private const val RevealLength = 0.5f

private val ProfileDots = Color(0xFF181818)

@Stable
internal class ProfileMorphState(private val scope: CoroutineScope, startOpen: Boolean) {
    var phase by mutableStateOf(if (startOpen) ProfilePhase.Open else ProfilePhase.Closed)
        private set

    private val start = if (startOpen) 1f else 0f

    /** The Mochy page zooming and fading out: 0 = untouched, 1 = gone. */
    val pageOut = Animatable(start)

    /** The profile page's black and dots coming up behind everything. */
    val backdrop = Animatable(start)

    /** Each flying part's trip: 0 = on the card, 1 = on the profile page. */
    val photo = Animatable(start)
    val name = Animatable(start)
    val number = Animatable(start)

    /** The rest of the profile page rising in, row by row; see [revealOf]. */
    val content = Animatable(start)

    /** 1 = the card leans with the finger and the phone, 0 = it holds the lean it had when the page took over. */
    val cardFollow = Animatable(if (startOpen) 0f else 1f)

    /** The card's lean right now. The card's own layer writes it whenever it changes. */
    var cardTiltX = 0f
    var cardTiltY = 0f

    /** The lean the card keeps while the profile page covers it. */
    var heldTiltX = 0f
        private set
    var heldTiltY = 0f
        private set

    /** The card's photo, name and number: where the copies take off from. */
    val card = IdCardAnchors()

    /** The card's slot on the Mochy page, outside its tilt. */
    var cardSlot: LayoutCoordinates? = null

    /** The Mochy page's own space, inside its zoom. */
    var pageRoot: LayoutCoordinates? = null

    /** The profile page's photo, name and number: where the copies land. Its root is the overlay. */
    val target = IdCardAnchors()

    /** The copies' own text, for lining their baselines up with both ends. */
    var flyingNameLayout: TextLayoutResult? = null
    var flyingNumberLayout: TextLayoutResult? = null
    var flyingPhotoReady by mutableStateOf(false)

    val isActive: Boolean get() = phase != ProfilePhase.Closed
    val isOpen: Boolean get() = phase == ProfilePhase.Open
    val isFlying: Boolean get() = phase == ProfilePhase.Opening || phase == ProfilePhase.Closing

    /** The card's own photo, name and number are hidden, because the copies or the profile page show them. */
    val coversCard: Boolean get() = phase == ProfilePhase.Opening || phase == ProfilePhase.Open || phase == ProfilePhase.Closing

    /**
     * The Mochy page is leaving or out of sight, so it can stop animating. It starts again as the
     * page comes back, while it's still invisible, so nothing visibly jumps.
     */
    val holdsPage: Boolean get() = phase == ProfilePhase.Opening || phase == ProfilePhase.Open

    private var job: Job? = null

    fun open() {
        if (phase != ProfilePhase.Closed) return
        // last time's measurements may be stale (a new name, a new picture); wait for fresh ones
        target.nameLayout = null
        target.numberLayout = null
        flyingNameLayout = null
        flyingNumberLayout = null
        flyingPhotoReady = false
        phase = ProfilePhase.Preparing
        job = scope.launch {
            // prepare, then animate: the page composes and lays out unseen first, so the flight's
            // first frame isn't also the page's most expensive one
            withTimeoutOrNull(PrepareTimeoutMs) { while (!partsReady()) withFrameNanos { } }
            withFrameNanos { }
            heldTiltX = cardTiltX
            heldTiltY = cardTiltY
            cardFollow.snapTo(0f)
            phase = ProfilePhase.Opening
            launch { content.animateTo(1f, tween(900, delayMillis = 320, easing = LinearEasing)) }
            coroutineScope {
                launch { pageOut.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
                launch { backdrop.animateTo(1f, tween(460, easing = LinearOutSlowInEasing)) }
                launch { photo.animateTo(1f, tween(720, easing = Emphasized)) }
                launch { name.animateTo(1f, tween(780, delayMillis = 40, easing = Emphasized)) }
                launch { number.animateTo(1f, tween(760, delayMillis = 110, easing = Emphasized)) }
            }
            phase = ProfilePhase.Open
        }
    }

    fun close() {
        when (phase) {
            ProfilePhase.Closed, ProfilePhase.Closing -> return
            ProfilePhase.Preparing -> {
                job?.cancel()
                phase = ProfilePhase.Closed
                return
            }
            else -> Unit
        }
        job?.cancel()
        phase = ProfilePhase.Closing
        job = scope.launch {
            launch { cardFollow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
            coroutineScope {
                launch { content.animateTo(0f, tween(240, easing = FastOutLinearInEasing)) }
                launch { number.animateTo(0f, tween(600, delayMillis = 40, easing = Emphasized)) }
                launch { name.animateTo(0f, tween(640, delayMillis = 70, easing = Emphasized)) }
                launch { photo.animateTo(0f, tween(640, delayMillis = 90, easing = Emphasized)) }
                launch { pageOut.animateTo(0f, tween(640, delayMillis = 110, easing = Emphasized)) }
                launch { backdrop.animateTo(0f, tween(480, delayMillis = 170, easing = FastOutSlowInEasing)) }
            }
            phase = ProfilePhase.Closed
        }
    }

    private fun partsReady(): Boolean =
        card.isReady && target.isReady && cardSlot?.isAttached == true && pageRoot?.isAttached == true &&
            flyingNameLayout != null && flyingNumberLayout != null && flyingPhotoReady

    // ---- where things are, in the overlay's space (the same as the Mochy page's, unzoomed) ----

    val pageScale: Float get() = 1f + PageZoom * pageOut.value

    /** The card's middle, which is what the Mochy page zooms around. */
    fun zoomPivot(): Offset? {
        val slot = cardSlot?.takeIf { it.isAttached } ?: return null
        val page = pageRoot?.takeIf { it.isAttached } ?: return null
        return page.localPositionOf(slot, Offset.Zero) + Offset(slot.size.width / 2f, slot.size.height / 2f)
    }

    /** [local] inside a part of the card: the card's tilt left out (the overlay adds it back), the page's zoom put in. */
    fun onCard(part: LayoutCoordinates?, local: Offset = Offset.Zero): Offset? {
        val cardRoot = card.root?.takeIf { it.isAttached } ?: return null
        val inside = part?.takeIf { it.isAttached } ?: return null
        val slot = cardSlot?.takeIf { it.isAttached } ?: return null
        val page = pageRoot?.takeIf { it.isAttached } ?: return null
        val slotTopLeft = page.localPositionOf(slot, Offset.Zero)
        val inPage = slotTopLeft + cardRoot.localPositionOf(inside, local)
        val pivot = slotTopLeft + Offset(slot.size.width / 2f, slot.size.height / 2f)
        return pivot + (inPage - pivot) * pageScale
    }

    /** [local] inside a part of the profile page. */
    fun onPage(part: LayoutCoordinates?, local: Offset = Offset.Zero): Offset? {
        val overlay = target.root?.takeIf { it.isAttached } ?: return null
        val inside = part?.takeIf { it.isAttached } ?: return null
        return overlay.localPositionOf(inside, local)
    }

    /** How far row [row] (and card [order] in it) has risen in, 0..1. */
    fun revealOf(row: Int, order: Int = 0): Float {
        val begin = row * RevealRowStep + order.coerceIn(0, 3) * RevealCardStep
        return Emphasized.transform(((content.value - begin) / RevealLength).coerceIn(0f, 1f))
    }

    companion object {
        /** Survives leaving for a playlist and coming back: the page is just open again, no flight. */
        fun saver(scope: CoroutineScope) = Saver<ProfileMorphState, Boolean>(
            save = { it.phase == ProfilePhase.Opening || it.phase == ProfilePhase.Open },
            restore = { ProfileMorphState(scope, startOpen = it) },
        )
    }
}

@Composable
internal fun rememberProfileMorphState(): ProfileMorphState {
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = ProfileMorphState.saver(scope)) { ProfileMorphState(scope, startOpen = false) }
}

/**
 * The Mochy page's half of the morph: it grows around the card and fades as the profile page
 * comes up. Goes on the page's root, outside everything it should carry along.
 */
internal fun Modifier.zoomsAwayFor(state: ProfileMorphState): Modifier = graphicsLayer {
    val out = state.pageOut.value
    if (out <= 0f) return@graphicsLayer
    val scale = state.pageScale
    scaleX = scale
    scaleY = scale
    state.zoomPivot()?.let { transformOrigin = TransformOrigin(it.x / size.width, it.y / size.height) }
    alpha = (1f - out * 1.15f).coerceIn(0f, 1f)
    // the page holds still meanwhile, so one cached picture zooms instead of the page redrawing each frame
    compositingStrategy = CompositingStrategy.Offscreen
}.onPlaced { state.pageRoot = it }

/** Fades a row of the profile page in and lifts it into place as [ProfileMorphState.content] runs. */
internal fun Modifier.reveal(state: ProfileMorphState, row: Int, order: Int = 0): Modifier = graphicsLayer {
    val shown = state.revealOf(row, order)
    alpha = shown
    translationY = (1f - shown) * 44f * density
    // each draw fades on its own: no offscreen pass per card per frame
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * The profile page and its flight, drawn over the Mochy page. Nothing at all while it's closed,
 * apart from keeping the page's lists loaded so it can open straight away.
 */
@Composable
internal fun ProfileMorphHost(
    state: ProfileMorphState,
    username: String,
    photo: Any?,
    cardNumber: String,
    navController: NavController,
    viewModel: UserProfileViewModel,
) {
    LaunchedEffect(viewModel) { viewModel.keepWarm() }
    // only showing up and going away recompose this; the steps in between are read while drawing
    val active by remember(state) { derivedStateOf { state.isActive } }
    if (!active) return

    ProfileBackHandler(state)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { state.target.root = it }
            // the overlay takes every touch, so nothing reaches the Mochy page underneath
            .pointerInput(Unit) {},
    ) {
        val name = idCardName(username)
        val nameMaxWidth = maxWidth - HomeContentInset * 2
        val nameStyle = rememberProfileNameStyle(name, nameMaxWidth)
        ProfileBackdrop(state)
        UserProfilePage(state, username, photo, cardNumber, nameStyle, navController, viewModel)
        FlyingParts(state, name, photo, cardNumber, nameStyle, nameMaxWidth)
    }
}

// on its own so the phase it reads doesn't recompose the whole page
@Composable
private fun ProfileBackHandler(state: ProfileMorphState) {
    BackHandler(enabled = state.phase == ProfilePhase.Opening || state.phase == ProfilePhase.Open) { state.close() }
}

/** Black, then the dots a beat behind it, so the Mochy page's own dots never sit on top of these. */
@Composable
private fun ProfileBackdrop(state: ProfileMorphState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = state.backdrop.value) }
            .recapDots(color = { ProfileDots }, alpha = { state.backdrop.value * state.backdrop.value }),
    )
}

/** The big name: 60sp, smaller when it wouldn't fit on one line, never below 34sp (then it ends in "…"). */
@Composable
private fun rememberProfileNameStyle(name: String, maxWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(name, maxWidth, density) {
        val maxPx = with(density) { maxWidth.roundToPx() }
        val width = measurer.measure(name, ProfileNameStyle, maxLines = 1, softWrap = false).size.width
        if (width <= maxPx) {
            ProfileNameStyle
        } else {
            val fitted = ProfileNameStyle.fontSize.value * maxPx / width * 0.98f
            ProfileNameStyle.copy(fontSize = fitted.coerceAtLeast(MinNameSp).sp)
        }
    }
}

private const val MinNameSp = 34f

/**
 * The copies that fly between the card and the page. They share one layer that leans the way the
 * card does, around the card's middle, and straightens out as they leave it.
 */
@Composable
private fun FlyingParts(
    state: ProfileMorphState,
    name: String,
    photo: Any?,
    cardNumber: String,
    nameStyle: TextStyle,
    nameMaxWidth: Dp,
) {
    LaunchedEffect(photo) { if (photo == null) state.flyingPhotoReady = true }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (state.isFlying) 1f else 0f
                val pivot = state.zoomPivot() ?: return@graphicsLayer
                val lean = 1f - state.photo.value
                rotationX = state.cardTiltX * lean
                rotationY = state.cardTiltY * lean
                transformOrigin = TransformOrigin(pivot.x / size.width, pivot.y / size.height)
                // the card's own camera, so the lean matches it exactly at take-off
                cameraDistance = 14f * density
            },
    ) {
        Box(
            modifier = Modifier
                .size(ProfilePhotoSize)
                .graphicsLayer { flyPhoto(state) }
                .background(ProfilePhotoGrey),
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state.flyingPhotoReady = true },
                    onError = { state.flyingPhotoReady = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = name,
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { state.flyingNameLayout = it },
            modifier = Modifier
                .widthIn(max = nameMaxWidth)
                .graphicsLayer {
                    flyText(state, state.name.value, state.card.name, state.card.nameLayout, state.target.name, state.target.nameLayout, state.flyingNameLayout, leadX = true)
                },
        )
        Text(
            text = profileTag(cardNumber),
            style = ProfileTagStyle,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { state.flyingNumberLayout = it },
            modifier = Modifier.graphicsLayer {
                flyText(state, state.number.value, state.card.number, state.card.numberLayout, state.target.number, state.target.numberLayout, state.flyingNumberLayout, leadX = false)
            },
        )
    }
}

/** Bends a straight line into a soft curve: one axis gets most of the way there before the other. */
private fun lead(t: Float): Float = 1f - (1f - t).pow(1.35f)

/** The photo copy: from the card's circle to the page's, rising first, then settling to the left. */
private fun GraphicsLayerScope.flyPhoto(state: ProfileMorphState) {
    val fromPart = state.card.photo
    val toPart = state.target.photo
    val from = state.onCard(fromPart)
    val to = state.onPage(toPart)
    if (from == null || to == null || fromPart == null || toPart == null || size.width <= 0f) {
        alpha = 0f
        return
    }
    val t = state.photo.value
    val width = lerpF(fromPart.size.width * state.pageScale, toPart.size.width.toFloat(), t)
    val scale = width / size.width
    translationX = lerpF(from.x, to.x, t)
    translationY = lerpF(from.y, to.y, lead(t))
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(0f, 0f)
    shape = CircleShape
    clip = true
}

/**
 * A text copy, pinned by its baseline's left end: it starts on the card's line at the card's size
 * and ends on the page's line at the page's size, growing in between.
 */
private fun GraphicsLayerScope.flyText(
    state: ProfileMorphState,
    t: Float,
    fromPart: LayoutCoordinates?,
    fromLayout: TextLayoutResult?,
    toPart: LayoutCoordinates?,
    toLayout: TextLayoutResult?,
    ownLayout: TextLayoutResult?,
    leadX: Boolean,
) {
    if (fromLayout == null || toLayout == null || ownLayout == null) {
        alpha = 0f
        return
    }
    val from = state.onCard(fromPart, Offset(0f, fromLayout.firstBaseline))
    val to = state.onPage(toPart, Offset(0f, toLayout.firstBaseline))
    val fromSize = fromLayout.layoutInput.style.fontSize
    val toSize = toLayout.layoutInput.style.fontSize
    if (from == null || to == null || !fromSize.isSp || !toSize.isSp) {
        alpha = 0f
        return
    }
    val startScale = fromSize.toPx() * state.pageScale / toSize.toPx()
    val scale = lerpF(startScale, 1f, t)
    translationX = lerpF(from.x, to.x, if (leadX) lead(t) else t)
    translationY = lerpF(from.y, to.y, t) - ownLayout.firstBaseline * scale
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(0f, 0f)
}
