package com.example.solidbg

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 已保存色板上限：满了会明确提示，绝不静默丢弃 */
internal const val SAVED_LIMIT = 12

/** 预置色（8 个，避免拥挤） */
private val PRESET_COLORS = listOf(
    Color(0xFF000000), Color(0xFFFFFFFF),
    Color(0xFF6E6E73), Color(0xFFE5484D),
    Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFF0A84FF)
)

/** 面板配色：跟随系统深色模式 */
internal data class PanelPalette(
    val panel: Color,
    val menuBg: Color,
    val field: Color,
    val text: Color,
    val hint: Color,
    val faint: Color,
    val border: Color,
    val buttonBg: Color,
    val buttonText: Color,
    val accent: Color,
    val danger: Color,
)

@Composable
internal fun panelPalette(): PanelPalette = if (isSystemInDarkTheme()) {
    PanelPalette(
        panel = Color(0xFF1B1B20),
        menuBg = Color(0xF01A1A1E),
        field = Color(0xFF2A2A32),
        text = Color(0xFFF3F3F5),
        hint = Color(0xFF9A9AA4),
        faint = Color(0xFF6E6E78),
        border = Color(0x33FFFFFF),
        buttonBg = Color(0xFF2C2C33),
        buttonText = Color(0xFFF3F3F5),
        accent = Color(0xFF3A6EE8),
        danger = Color(0xFFE5484D)
    )
} else {
    PanelPalette(
        panel = Color(0xFFF9F9FC),
        menuBg = Color(0xF7FFFFFF),
        field = Color(0xFFEBEBF1),
        text = Color(0xFF141418),
        hint = Color(0xFF63636E),
        faint = Color(0xFF8C8C97),
        border = Color(0x22000000),
        buttonBg = Color(0xFFE7E7EE),
        buttonText = Color(0xFF141418),
        accent = Color(0xFF2F62D6),
        danger = Color(0xFFD93A3F)
    )
}

internal fun isLightColor(argb: Int): Boolean = AColor.luminance(argb) > 0.6f

internal fun hexOf(argb: Int): String = "#%06X".format(0xFFFFFF and argb)

internal fun hexBody(argb: Int): String = "%06X".format(0xFFFFFF and argb)

/**
 * 调色盘：不再是盖住整屏的蒙版页，而是一个直接浮在纯色背景上的小窗口
 * （背景不做任何压暗，拖动时能直接看到真实底色）。
 */
@Composable
internal fun ColorPickerPanel(
    color: Int,
    savedColors: List<Int>,
    onChange: (Int) -> Unit,
    onSaveColor: (Int) -> Unit,
    onRemoveSaved: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val p = panelPalette()
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    var notice by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current

    fun push() {
        val h = hue.coerceIn(0f, 359.9f)
        val s = sat.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        onChange(AColor.HSVToColor(floatArrayOf(h, s, v)))
    }

    // 只有“外部来源”（预设 / 已保存 / 精确输入）改色时才回填 HSV 状态
    LaunchedEffect(color) {
        val current = AColor.HSVToColor(
            floatArrayOf(hue.coerceIn(0f, 359.9f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
        )
        if (current != color) {
            val hsv = FloatArray(3)
            AColor.colorToHSV(color, hsv)
            if (hsv[2] > 0.02f) hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    LaunchedEffect(notice) {
        if (notice.isNotEmpty()) {
            delay(2400)
            notice = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = p.panel,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 300.dp)
                .border(1.dp, p.border, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 标题行：小色块 + HEX（原版布局）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("调色盘", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(1.dp, p.border, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = hexOf(color), color = p.hint, fontSize = 11.sp)
                }

                Spacer(Modifier.height(12.dp))

                SaturationValuePanel(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { s, v ->
                        sat = s
                        value = v
                        push()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(Modifier.height(10.dp))

                HueBar(
                    hue = hue,
                    onChange = { h ->
                        hue = h
                        push()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )

                Spacer(Modifier.height(14.dp))

                PreciseInputs(color = color, onChange = onChange, palette = p)

                Spacer(Modifier.height(14.dp))

                Text(text = "预设", color = p.faint, fontSize = 10.sp)

                Spacer(Modifier.height(6.dp))

                PresetGrid(current = color, palette = p, onPick = { onChange(it) })

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已保存 ${savedColors.size} / $SAVED_LIMIT",
                        color = p.faint,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                when {
                                    savedColors.contains(color) -> notice = "这个颜色已经在色板里了"
                                    savedColors.size >= SAVED_LIMIT ->
                                        notice = "色板已满（$SAVED_LIMIT 个），长按色块删一个再存"
                                    else -> {
                                        onSaveColor(color)
                                        notice = "已保存 ${hexOf(color)}"
                                    }
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save),
                            contentDescription = "保存颜色",
                            tint = p.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "保存颜色",
                            color = p.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                SavedGrid(
                    savedColors = savedColors,
                    current = color,
                    palette = p,
                    onPick = { onChange(it) },
                    onRequestDelete = { pendingDelete = it }
                )

                if (notice.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = notice, color = p.hint, fontSize = 11.sp)
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.accent,
                        contentColor = Color.White
                    )
                ) {
                    Text("完成", fontSize = 13.sp)
                }
            }
        }

        // 删除二次确认
        pendingDelete?.let { target ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable { pendingDelete = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = p.panel,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 260.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(target))
                                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("删除这个已保存的颜色？", color = p.text, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(hexOf(target), color = p.hint, fontSize = 11.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pendingDelete = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = p.buttonBg,
                                    contentColor = p.buttonText
                                )
                            ) {
                                Text("取消", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    onRemoveSaved(target)
                                    pendingDelete = null
                                    notice = "已删除 ${hexOf(target)}"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = p.danger,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("删除", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** RGB 数字输入 + HEX 输入，用于精准调色 */
@Composable
private fun PreciseInputs(
    color: Int,
    onChange: (Int) -> Unit,
    palette: PanelPalette,
) {
    val r0 = (color shr 16) and 0xFF
    val g0 = (color shr 8) and 0xFF
    val b0 = color and 0xFF

    var rText by remember { mutableStateOf(r0.toString()) }
    var gText by remember { mutableStateOf(g0.toString()) }
    var bText by remember { mutableStateOf(b0.toString()) }
    var hexText by remember { mutableStateOf(hexBody(color)) }
    var hexFocused by remember { mutableStateOf(false) }

    LaunchedEffect(r0) { if (rText.toIntOrNull() != r0) rText = r0.toString() }
    LaunchedEffect(g0) { if (gText.toIntOrNull() != g0) gText = g0.toString() }
    LaunchedEffect(b0) { if (bText.toIntOrNull() != b0) bText = b0.toString() }
    LaunchedEffect(color) { if (!hexFocused) hexText = hexBody(color) }

    fun emit(r: String, g: String, b: String) {
        val rr = r.toIntOrNull()?.coerceIn(0, 255) ?: return
        val gg = g.toIntOrNull()?.coerceIn(0, 255) ?: return
        val bb = b.toIntOrNull()?.coerceIn(0, 255) ?: return
        onChange((0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb)
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChannelField("R", rText, palette,
                onValue = { v ->
                    rText = v
                    emit(v, gText, bText)
                },
                modifier = Modifier.weight(1f))
            ChannelField("G", gText, palette,
                onValue = { v ->
                    gText = v
                    emit(rText, v, bText)
                },
                modifier = Modifier.weight(1f))
            ChannelField("B", bText, palette,
                onValue = { v ->
                    bText = v
                    emit(rText, gText, v)
                },
                modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.field)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "#", color = palette.hint, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = hexText,
                onValueChange = { raw ->
                    val filtered = raw.uppercase()
                        .filter { c -> c in '0'..'9' || c in 'A'..'F' }
                        .take(6)
                    hexText = filtered
                    if (filtered.length == 6) {
                        val v = filtered.toLongOrNull(16)?.toInt()
                        if (v != null) onChange((0xFF shl 24) or (v and 0xFFFFFF))
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        hexFocused = state.isFocused
                        if (!state.isFocused) hexText = hexBody(color)
                    }
            )
            Text(text = "HEX", color = palette.faint, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ChannelField(
    label: String,
    value: String,
    palette: PanelPalette,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.field)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = palette.hint, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = { raw -> onValue(raw.filter { it in '0'..'9' }.take(3)) },
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var change = down
                        while (true) {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val x = change.position.x.coerceIn(0f, w)
                            val y = change.position.y.coerceIn(0f, h)
                            onChange(x / w, 1f - y / h)
                            change.consume()
                            if (!change.pressed) break
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                        }
                    }
                }
            }
    ) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.White, Color.hsv(hue.coerceIn(0f, 359.9f), 1f, 1f))
            )
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val cx = sat.coerceIn(0f, 1f) * size.width
        val cy = (1f - value.coerceIn(0f, 1f)) * size.height
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(2.dp.toPx())
        )
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var change = down
                        while (true) {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = change.position.x.coerceIn(0f, w) / w
                            onChange(fraction * 359.9f)
                            change.consume()
                            if (!change.pressed) break
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                        }
                    }
                }
            }
    ) {
        val colors = List(7) { index -> Color.hsv((index * 60f) % 360f, 1f, 1f) }
        drawRect(Brush.horizontalGradient(colors))

        val x = (hue.coerceIn(0f, 359.9f) / 359.9f) * size.width
        val cy = size.height / 2f
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(x, cy),
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = Offset(x, cy),
            style = Stroke(2.dp.toPx())
        )
    }
}

@Composable
private fun PresetGrid(
    current: Int,
    palette: PanelPalette,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PRESET_COLORS.chunked(4).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowColors.forEach { swatch ->
                    val argb = swatch.toArgb()
                    val selected = argb == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(swatch)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(0xFF7AB8FF) else palette.border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onPick(argb) }
                    )
                }
                repeat(4 - rowColors.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedGrid(
    savedColors: List<Int>,
    current: Int,
    palette: PanelPalette,
    onPick: (Int) -> Unit,
    onRequestDelete: (Int) -> Unit,
) {
    if (savedColors.isEmpty()) {
        Text(
            text = "还没有保存的颜色，点右上「保存颜色」",
            color = palette.faint,
            fontSize = 10.sp
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        savedColors.chunked(6).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowColors.forEach { argb ->
                    val selected = argb == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(argb))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(0xFF7AB8FF) else palette.border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = { onPick(argb) },
                                onLongClick = { onRequestDelete(argb) }
                            )
                    )
                }
                repeat(6 - rowColors.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
