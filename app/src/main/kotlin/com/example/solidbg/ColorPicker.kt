package com.example.solidbg

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRESET_COLORS = listOf(
    Color(0xFF000000), Color(0xFF141418), Color(0xFF2C2C33), Color(0xFF6E6E73),
    Color(0xFFC7C7CC), Color(0xFFFFFFFF),
    Color(0xFFE5484D), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFF40C8E0), Color(0xFF0A84FF),
    Color(0xFF3D3AD6), Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFFA2845E),
    Color(0xFF0E6E5C), Color(0xFF1B2A41)
)

internal fun isLightColor(argb: Int): Boolean = AColor.luminance(argb) > 0.6f

internal fun hexOf(argb: Int): String = "#%06X".format(0xFFFFFF and argb)

@Composable
internal fun ColorPickerPanel(
    color: Int,
    onChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }

    fun push() {
        val h = hue.coerceIn(0f, 359.9f)
        val s = sat.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        onChange(AColor.HSVToColor(floatArrayOf(h, s, v)))
    }

    fun loadFrom(argb: Int) {
        val hsv = FloatArray(3)
        AColor.colorToHSV(argb, hsv)
        if (hsv[2] > 0.02f) hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    LaunchedEffect(Unit) { loadFrom(color) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xA6000000))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF18181C),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 380.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "调色盘",
                        color = Color(0xFFF3F3F5),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = hexOf(color), color = Color(0xFF9A9AA4), fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))

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
                        .height(170.dp)
                )

                Spacer(Modifier.height(12.dp))

                HueBar(
                    hue = hue,
                    onChange = { h ->
                        hue = h
                        push()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )

                Spacer(Modifier.height(18.dp))

                Text(text = "预设", color = Color(0xFF9A9AA4), fontSize = 11.sp)

                Spacer(Modifier.height(8.dp))

                PresetGrid(
                    current = color,
                    onPick = { argb ->
                        loadFrom(argb)
                        onChange(argb)
                    }
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C33),
                        contentColor = Color(0xFFF3F3F5)
                    )
                ) {
                    Text("完成", fontSize = 14.sp)
                }
            }
        }
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
            .clip(RoundedCornerShape(14.dp))
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
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
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
            radius = 12.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(2.5.dp.toPx())
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
            .clip(RoundedCornerShape(13.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var change = down
                        while (true) {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = change.position.x.coerceIn(0f, w) / w
                            onChange(fraction * 359.9f)
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
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
            radius = 13.dp.toPx(),
            center = Offset(x, cy),
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 11.dp.toPx(),
            center = Offset(x, cy),
            style = Stroke(2.5.dp.toPx())
        )
    }
}

@Composable
private fun PresetGrid(
    current: Int,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESET_COLORS.chunked(6).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowColors.forEach { swatch ->
                    val argb = swatch.toArgb()
                    val selected = argb == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(swatch)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color(0x26FFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onPick(argb) }
                    )
                }
            }
        }
    }
}
