package app.orchard.tv.ui.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.topshelf.ShelfImages
import app.orchard.tv.topshelf.ShelfKind
import app.orchard.tv.topshelf.ShelfProgram
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.launch

class ShelfNavigation { var enter: (() -> Unit)? = null }

@Composable
fun TopShelf(
    packageName: String,
    label: String,
    programs: List<ShelfProgram>,
    navigation: ShelfNavigation,
    cardHeight: Dp,
    onFocus: (Boolean) -> Unit,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onOpen: (ShelfProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val groups = remember(programs) { ShelfKind.entries.filter { kind -> programs.any { it.kind == kind } } }
    var kind by remember(packageName) { mutableStateOf(groups.first()) }
    val selectedKind = kind.takeIf { it in groups } ?: groups.first()
    val items = remember(programs, selectedKind) { programs.filter { it.kind == selectedKind } }
    val list = rememberLazyListState()
    val requesters = remember(packageName) { HashMap<String, FocusRequester>() }
    val tabs = remember(packageName) { ShelfKind.entries.associateWith { FocusRequester() } }
    var selectedId by remember(packageName) { mutableStateOf<String?>(null) }
    var hadFocus by remember(packageName) { mutableStateOf(false) }
    val focusedNow by rememberUpdatedState(hadFocus)
    val itemsNow by rememberUpdatedState(items)
    val selectedNow by rememberUpdatedState(selectedId)

    fun focusCard(index: Int) {
        scope.launch {
            val item = items.getOrNull(index) ?: return@launch
            list.scrollToItem(index)
            withFrameNanos { }
            runCatching { requesters.getOrPut(item.id) { FocusRequester() }.requestFocus() }
        }
    }
    DisposableEffect(packageName, items) {
        navigation.enter = { focusCard(items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) }
        onDispose { navigation.enter = null }
    }
    // A provider update may remove a focused card while the user is browsing it.
    LaunchedEffect(items) {
        if (focusedNow && itemsNow.none { it.id == selectedNow }) focusCard(0)
    }

    Column(modifier.onFocusChanged { hadFocus = it.hasFocus; onFocus(it.hasFocus) }.focusGroup()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = Type.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            BasicText(stringResource(R.string.ts_up_hint), style = Type.Caption)
        }
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEachIndexed { index, group ->
                Pressable(
                    onClick = { kind = group; selectedId = null }, focusRequester = tabs.getValue(group),
                    modifier = Modifier.onFocusChanged { if (it.isFocused) { kind = group; selectedId = null } },
                    onKey = { e -> direction(e) { code ->
                        when (code) {
                            NativeKeyEvent.KEYCODE_DPAD_DOWN -> focusCard(0)
                            NativeKeyEvent.KEYCODE_DPAD_UP -> onUp()
                            NativeKeyEvent.KEYCODE_DPAD_LEFT -> if (index > 0) tabs.getValue(groups[index - 1]).requestFocus()
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> if (index < groups.lastIndex) tabs.getValue(groups[index + 1]).requestFocus()
                        }
                    } },
                ) { state ->
                    BasicText(
                        stringResource(if (group == ShelfKind.Continue) R.string.ts_continue else R.string.ts_recommend),
                        style = Type.Callout.copy(color = if (state.focused || group == selectedKind) Palette.OnFocus else Palette.Label),
                        modifier = Modifier.background(if (state.focused || group == selectedKind) Palette.Focus else Palette.FillStrong, RoundedCornerShape(30.dp))
                            .then(if (state.focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(30.dp)) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth().height(cardHeight + 54.dp)) {
            itemsIndexed(items, key = { _, p -> p.id }) { index, program ->
                ShelfCard(
                    program, cardHeight, requesters.getOrPut(program.id) { FocusRequester() },
                    onFocused = { selectedId = program.id }, onOpen = { onOpen(program) },
                    onKey = { e -> direction(e) { code ->
                        when (code) {
                            NativeKeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                            NativeKeyEvent.KEYCODE_DPAD_UP -> tabs.getValue(selectedKind).requestFocus()
                            NativeKeyEvent.KEYCODE_DPAD_LEFT -> focusCard((index - 1).coerceAtLeast(0))
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> focusCard((index + 1).coerceAtMost(items.lastIndex))
                        }
                    } },
                )
            }
        }
    }
}

/** Consume both halves and held repeats of a directional press; the first down makes one move. */
private fun direction(event: KeyEvent, move: (Int) -> Unit): Boolean {
    val code = event.nativeKeyEvent.keyCode
    if (code !in listOf(NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT, NativeKeyEvent.KEYCODE_DPAD_RIGHT)) return false
    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) move(code)
    return true
}

@Composable
private fun ShelfCard(
    program: ShelfProgram, height: Dp, requester: FocusRequester,
    onFocused: () -> Unit, onOpen: () -> Unit, onKey: (KeyEvent) -> Boolean,
) {
    val context = LocalContext.current.applicationContext
    val picture by produceState<ImageBitmap?>(null, program.posterUri) {
        value = null
        program.posterUri?.let { value = ShelfImages.load(context, it)?.asImageBitmap() }
    }
    Pressable(
        onClick = onOpen, focusRequester = requester, focusedScale = 1.035f, onKey = onKey,
        modifier = Modifier.padding(top = 5.dp, start = 5.dp, end = 5.dp).width(height * 16 / 9)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) { state ->
        Column {
            Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(10.dp))
                .background(Palette.Glass)
                .then(if (state.focused) Modifier.border(3.dp, Palette.Focus, RoundedCornerShape(10.dp)) else Modifier)) {
                picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: BasicText(program.title, style = Type.Title, maxLines = 2, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                program.progress?.let { progress ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Palette.FillStrong)) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Palette.Focus))
                    }
                }
            }
            BasicText(program.title, style = Type.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            program.progress?.let {
                BasicText(stringResource(R.string.ts_progress, (it * 100).toInt()), style = Type.Caption)
            }
        }
    }
}
