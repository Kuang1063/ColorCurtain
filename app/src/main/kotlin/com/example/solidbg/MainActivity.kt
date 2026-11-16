package com.example.solidbg

import android.graphics.Color as AColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlin.math.sqrt

private const val PREFS_NAME = "solid_bg_prefs"
private const val KEY_COLOR = "bg_color"
private const val KEY_SAVED = "saved_colors"
private const val MENU_TIMEOUT_MS = 10_000L
private const val SWIPE_STREAK_WINDOW_MS = 4_000L

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private var barsVisible = false
    private var backgroundColor = AColor.BLACK
    private var savedColors: List<Int> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundColor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_COLOR, AColor.BLACK)
        savedColors = readSavedColors()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AColor.TRANSPARENT
        window.navigationBarColor = AColor.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))

        val startColor = backgroundColor
        setContent {
            SolidBgApp(
                initialColor = startColor,
                initialSaved = savedColors,
                onSavedColorsChanged = { list ->
                    savedColors = list
                    writeSavedColors(list)
                },
                onColorChanged = { color ->
                    backgroundColor = color
                    applySystemBars()
                },
                onBarsVisibilityChanged = { visible ->
                    barsVisible = visible
                    applySystemBars()
                },
                onExit = { finishAffinity() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_COLOR, backgroundColor)
            .apply()
    }

    private fun applySystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // 单次滑动只会临时浮出系统栏并自动隐藏，需要“二次滑动”才会常驻
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (barsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        val lightIcons = isLightColor(backgroundColor)
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
    }

    private fun readSavedColors(): List<Int> {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_SAVED, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.take(24)
    }

    private fun writeSavedColors(list: List<Int>) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED, list.take(24).joinToString(","))
            .apply()
    }
}

@Composable
private fun SolidBgApp(
    initialColor: Int,
    initialSaved: List<Int>,
    onSavedColorsChanged: (List<Int>) -> Unit,
    onColorChanged: (Int) -> Unit,
    onBarsVisibilityChanged: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    var bg by remember { mutableIntStateOf(initialColor) }
    var savedColors by remember { mutableStateOf(initialSaved) }
    var barsVisible by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    var hintText by remember { mutableStateOf("") }
    var hintVisible by remember { mutableStateOf(false) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var swipeStreak by remember { mutableIntStateOf(0) }
    var lastGestureAt by remember { mutableLongStateOf(0L) }
    var idleTick by remember { mutableIntStateOf(0) }
    var remainMs by remember { mutableIntStateOf(MENU_TIMEOUT_MS.toInt()) }

    val ink = if (isLightColor(bg)) Color(0xFF101014) else Color(0xFFF3F3F5)

    LaunchedEffect(bg) { onColorChanged(bg) }
    LaunchedEffect(barsVisible) { onBarsVisibilityChanged(barsVisible) }

    LaunchedEffect(hintVisible, hintText) {
        if (hintVisible) {
            delay(1600)
            hintVisible = false
        }
    }

    LaunchedEffect(menuOpen, idleTick) {
        if (!menuOpen) return@LaunchedEffect
        remainMs = MENU_TIMEOUT_MS.toInt()
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val left = MENU_TIMEOUT_MS - (SystemClock.elapsedRealtime() - startedAt)
            remainMs = if (left > 0L) left.toInt() else 0
            if (left <= 0L) {
                menuOpen = false
                hintVisible = false
                break
            }
            delay(60)
        }
    }

    fun showHint(text: String) {
        hintText = text
        hintVisible = true
    }

    // 一次手势：隐藏 -> 计数；第二次手势 -> 显示系统栏。再次手势则隐藏。
    fun onSystemBarGesture() {
        val now = SystemClock.elapsedRealtime()
        if (pickerOpen) {
            pickerOpen = false
            return
        }
        if (menuOpen) {
            menuOpen = false
            return
        }
        if (barsVisible) {
            barsVisible = false
            swipeStreak = 0
            lastGestureAt = now
            showHint("系统栏已隐藏")
            return
        }
        if (now - lastGestureAt > SWIPE_STREAK_WINDOW_MS) swipeStreak = 0
        lastGestureAt = now
        swipeStreak += 1
        if (swipeStreak >= 2) {
            swipeStreak = 0
            barsVisible = true
            showHint("已显示系统栏")
        } else {
            showHint("再滑动一次，显示系统栏")
        }
    }

    fun openMenu() {
        menuOpen = true
        idleTick += 1
        hintVisible = false
    }

    BackHandler(enabled = true) { onSystemBarGesture() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(bg))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                val swipeThreshold = 42.dp.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 按下时若已有浮层，本次手势完全交给浮层处理，
                        // 避免「关掉菜单的那一下」又被背景手势当成点击而重新弹出
                        val blockedAtDown = menuOpen || pickerOpen
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                        }
                        if (blockedAtDown) continue
                        val dx = last.x - down.position.x
                        val dy = last.y - down.position.y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance >= swipeThreshold) {
                            onSystemBarGesture()
                        } else {
                            val w = canvasSize.width.toFloat()
                            val h = canvasSize.height.toFloat()
                            if (w > 0f && h > 0f) {
                                val inCenterX = down.position.x > w * 0.25f && down.position.x < w * 0.75f
                                val inCenterY = down.position.y > h * 0.25f && down.position.y < h * 0.75f
                                if (inCenterX && inCenterY) openMenu()
                            }
                        }
                    }
                }
            }
    ) {
        // 手势反馈提示（短暂淡出，不影响纯色观感）
        AnimatedVisibility(
            visible = hintVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = hintText,
                color = ink.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }

        AnimatedVisibility(
            visible = menuOpen,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            MenuCard(
                remainMs = remainMs,
                onInteract = { idleTick += 1 },
                onPalette = {
                    menuOpen = false
                    pickerOpen = true
                },
                onClose = { menuOpen = false },
                onExit = onExit
            )
        }

        if (pickerOpen) {
            ColorPickerPanel(
                color = bg,
                savedColors = savedColors,
                onChange = { bg = it },
                onSaveColor = { argb ->
                    val next = (listOf(argb) + savedColors.filter { it != argb }).take(SAVED_LIMIT)
                    savedColors = next
                    onSavedColorsChanged(next)
                },
                onRemoveSaved = { argb ->
                    val next = savedColors.filter { it != argb }
                    savedColors = next
                    onSavedColorsChanged(next)
                },
                onClose = { pickerOpen = false }
            )
        }
    }
}

@Composable
private fun MenuCard(
    remainMs: Int,
    onInteract: () -> Unit,
    onPalette: () -> Unit,
    onClose: () -> Unit,
    onExit: () -> Unit,
) {
    val seconds = (remainMs + 999) / 1000
    val p = panelPalette()
    Column(
        modifier = Modifier
            .width(294.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(p.menuBg)
            .border(1.dp, p.border, RoundedCornerShape(26.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onInteract()
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "菜单",
                color = p.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(p.field)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", color = p.hint, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$seconds 秒后自动关闭",
            color = p.hint,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPalette,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = p.buttonBg,
                    contentColor = p.buttonText
                )
            ) {
                Text("调色盘", fontSize = 13.sp)
            }
            Button(
                onClick = onExit,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = p.danger,
                    contentColor = Color.White
                )
            ) {
                Text("退出", fontSize = 13.sp)
            }
        }
    }
}
