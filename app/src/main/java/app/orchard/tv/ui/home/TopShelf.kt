package app.orchard.tv.ui.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.system.Sounds
import app.orchard.tv.topshelf.*
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

class ShelfNavigation { var enter: (() -> Unit)? = null }

@Composable
fun TopShelf(
    packageName: String, label: String, programs: List<ShelfProgram>, navigation: ShelfNavigation,
    cardHeight: Dp, onFocus: (Boolean) -> Unit, onDown: () -> Unit, onUp: () -> Unit,
    onOpen: (ShelfProgram) -> Unit, modifier: Modifier = Modifier,
    onHide: (ShelfProgram) -> Unit = {}, onDisable: () -> Unit = {}, interactive: Boolean = true,
) {
    if (programs.isEmpty()) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduced = LocalReducedMotion.current
    val requesters = remember(packageName) { HashMap<String, FocusRequester>() }
    var selectedId by remember(packageName) { mutableStateOf<String?>(null) }
    var focused by remember(packageName) { mutableStateOf(false) }
    var menu by remember(packageName) { mutableStateOf<ShelfProgram?>(null) }
    var menuKeyStarted by remember { mutableStateOf(false) }
    val index = programs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selected = programs[index]
    val width = cardHeight * (196f / 110f)
    val pitch = width + 20.dp
    val scroll = remember(packageName) { Animatable(0f) }
    fun request(index: Int) {
        val item = programs.getOrNull(index) ?: return
        selectedId = item.id
        scope.launch {
            // Selection mounts the target before the next focus request, even beyond the viewport.
            withFrameNanos { }
            runCatching { requesters.getOrPut(item.id) { FocusRequester() }.requestFocus() }
        }
    }
    DisposableEffect(packageName, programs, interactive) {
        if (interactive) navigation.enter = { request(programs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) }
        onDispose { if (interactive) navigation.enter = null }
    }
    // Capture before composition removes the old focus target. Reading focused inside the
    // coroutine would observe the subsequent focus-loss event and miss recovery entirely.
    val recoverRemovedSelection = focused && selectedId != null && programs.none { it.id == selectedId }
    LaunchedEffect(programs) {
        if (interactive && recoverRemovedSelection) request(0)
    }
    fun closeMenu() { menu = null; request(index) }
    BackHandler(enabled = interactive && menu != null) { closeMenu() }
    val type = programType(selected)
    Column(modifier.onFocusChanged { focused = it.hasFocus; if (interactive) onFocus(it.hasFocus) }.focusGroup()) {
        BasicText("$label · $type", style = Type.Callout.copy(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(cardHeight + 64.dp).clipToBounds()) {
            val slots = ((maxWidth - 40.dp + 20.dp) / pitch).toInt().coerceAtLeast(1)
            val maxScroll = with(density) { (pitch * programs.size - 20.dp - (maxWidth - 40.dp)).toPx().coerceAtLeast(0f) }
            val target = with(density) { (pitch * (index - slots + 1).coerceAtLeast(0)).toPx() }.coerceAtMost(maxScroll)
            LaunchedEffect(target) {
                if (reduced) scroll.snapTo(target)
                else scroll.animateTo(target, spring(dampingRatio = 1f, stiffness = 320f))
            }
            val pitchPx = with(density) { pitch.toPx() }
            val firstVisible by remember(pitchPx) { derivedStateOf { floor(scroll.value / pitchPx).toInt() } }
            programs.forEachIndexed { i, program ->
                if (i in (firstVisible - 1)..(firstVisible + slots + 1) || i == index) key(program.id) {
                    ShelfCard(
                        program, width, cardHeight, requesters.getOrPut(program.id) { FocusRequester() },
                        enabled = interactive && menu == null,
                        onFocused = { selectedId = program.id }, onOpen = { onOpen(program) },
                        onMenu = { menu = program },
                        onKey = { e, nudge ->
                            val code = e.nativeKeyEvent.keyCode
                            if (code == NativeKeyEvent.KEYCODE_MENU) {
                                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) menuKeyStarted = true
                                if (e.type == KeyEventType.KeyUp && menuKeyStarted) { menuKeyStarted = false; menu = program }
                                true
                            } else direction(e) { key -> when (key) {
                                NativeKeyEvent.KEYCODE_DPAD_UP -> onUp()
                                NativeKeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                                NativeKeyEvent.KEYCODE_DPAD_LEFT -> if (i > 0) request(i - 1) else { Sounds.limit(); nudge(-1f) }
                                NativeKeyEvent.KEYCODE_DPAD_RIGHT -> if (i < programs.lastIndex) request(i + 1) else { Sounds.limit(); nudge(1f) }
                            } }
                        },
                        modifier = Modifier.offset { IntOffset(with(density) { (20.dp + pitch * i).roundToPx() } - scroll.value.roundToInt(), with(density) { 24.dp.roundToPx() }) },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).height(44.dp)) {
            BasicText(selected.title, style = Type.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(programSubtitle(selected), style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (menu != null && interactive) {
        val item = menu!!
        val first = remember { FocusRequester() }
        Popup(alignment = Alignment.Center, onDismissRequest = { closeMenu() }, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(330.dp).liquidGlass(18.dp).padding(12.dp)) {
                BasicText(item.title, style = Type.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
                MenuRow(Glyph.Play, stringResource(R.string.ts_play), onClick = { menu = null; onOpen(item) }, focusRequester = first)
                MenuRow(Glyph.EyeSlash, stringResource(R.string.ts_hide), onClick = { menu = null; onHide(item) })
                MenuRow(Glyph.EyeSlash, stringResource(R.string.ts_disable_app), onClick = { menu = null; onDisable() })
            }
            LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
        }
    }
}

@Composable
private fun programType(program: ShelfProgram): String = stringResource(when {
    program.kind == ShelfKind.Recommend -> R.string.ts_recommend
    program.watchNextType == 1 -> R.string.ts_next
    program.watchNextType == 2 -> R.string.ts_new
    program.watchNextType == 3 -> R.string.ts_watchlist
    else -> R.string.ts_continue
})

@Composable
private fun programSubtitle(program: ShelfProgram): String {
    val parts = mutableListOf<String>()
    if (!program.season.isNullOrBlank() && !program.episode.isNullOrBlank()) parts += "S${program.season.padStart(2, '0')}E${program.episode.padStart(2, '0')}"
    else if (!program.episode.isNullOrBlank()) parts += stringResource(R.string.ts_episode, program.episode)
    program.episodeTitle?.takeIf { it != program.title }?.let { parts += it }
    program.remainingMinutes?.let { parts += stringResource(R.string.ts_remaining, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
    program.channelName?.let { parts += it }
    if (parts.isEmpty()) program.description?.let { parts += it }
    return parts.joinToString(" · ")
}

private fun direction(event: KeyEvent, move: (Int) -> Unit): Boolean {
    val code = event.nativeKeyEvent.keyCode
    if (code !in listOf(NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_DOWN, NativeKeyEvent.KEYCODE_DPAD_LEFT, NativeKeyEvent.KEYCODE_DPAD_RIGHT)) return false
    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) move(code)
    return true
}

@Composable
private fun ShelfCard(
    program: ShelfProgram, width: Dp, height: Dp, requester: FocusRequester, enabled: Boolean,
    onFocused: () -> Unit, onOpen: () -> Unit, onMenu: () -> Unit,
    onKey: (KeyEvent, (Float) -> Unit) -> Boolean, modifier: Modifier,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val push = remember { Animatable(0f) }
    val w = (width.value * density).roundToInt(); val h = (height.value * density).roundToInt()
    val picture by produceState<ImageBitmap?>(null, program.posterUri, w, h) {
        value = null
        program.posterUri?.let { value = ShelfImages.load(context, it, w, h)?.asImageBitmap() }
    }
    val decorations by produceState<Pair<TileShadow, RingFrame>?>(null, w, h, density) {
        value = withContext(Dispatchers.Default) { tileShadow(w, h, w * 12f / 196f, 14 * density) to ringFrame(w, h, w * 12f / 196f, 4 * density, 2.5f * density, density) }
    }
    fun nudge(sign: Float) { if (!reduced) scope.launch {
        push.snapTo(sign)
        push.animateTo(0f, spring(dampingRatio = .65f, stiffness = 520f))
    } }
    Pressable(
        onClick = onOpen, onLongPress = onMenu, focusRequester = requester, enabled = enabled,
        focusedScale = 1.09f, onKey = { onKey(it, ::nudge) },
        modifier = modifier.size(width, height).onFocusChanged { if (it.isFocused) onFocused() }
            .graphicsLayer { translationX = push.value * 6 * density; rotationY = push.value * 4f },
    ) { state ->
        Canvas(Modifier.fillMaxSize()) {
            if (state.focused && !AerialSettings.lite) decorations?.first?.let { shadow ->
                translate(-shadow.ext, -shadow.ext + 10 * density) { drawImage(shadow.image) }
            }
            if (picture == null) drawRoundRect(Palette.Glass, cornerRadius = CornerRadius(w * 12f / 196f))
            picture?.let { drawImage(it) }
            if (program.kind == ShelfKind.Continue && program.durationMs > 0) {
                val bar = 3 * density; val pad = 10 * density
                val track = size.width - pad * 2; val y = size.height - 8 * density - bar
                drawRoundRect(Color.White.copy(alpha = .32f), Offset(pad, y), Size(track, bar), CornerRadius(bar / 2))
                drawRoundRect(Color.White.copy(alpha = if (state.focused) 1f else .85f), Offset(pad, y), Size(track * (program.progress ?: 0f), bar), CornerRadius(bar / 2))
            }
            if (state.focused && AerialSettings.focusStyle == FocusStyle.HighContrast) decorations?.second?.let { ring ->
                translate(-ring.ext, -ring.ext) { drawImage(ring.image) }
            }
        }
        if (picture == null) BasicText(program.title, style = Type.Callout, maxLines = 2, modifier = Modifier.align(Alignment.Center).padding(12.dp))
        if (program.kind == ShelfKind.Continue && height >= 70.dp) BasicText(programType(program), style = Type.Caption.copy(color = Palette.Label),
            modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = .6f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
