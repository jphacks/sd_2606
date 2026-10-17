package jp.jphacks.sd2606.ui.meter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.meter.IncomeWall
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

/** ビーカーに積む1層（1回分の給料）。 */
data class BeakerLayer(val amount: Long, val color: Color)

/**
 * ひびの形。ビーカーの内側を 0〜1 とした座標（x: 左→右、y: 縁→底）の折れ線。
 * 壁を1つ超えるごとに1グループずつ増え、全部超えると割れた状態になる。
 */
private val CrackGroups = listOf(
    // 1つ目: 右の壁に小さなひび
    listOf(listOf(1.00f to 0.30f, 0.88f to 0.34f, 0.92f to 0.40f)),
    // 2つ目: 左の壁からも入る
    listOf(listOf(0.00f to 0.55f, 0.14f to 0.60f, 0.10f to 0.68f, 0.26f to 0.76f)),
    // 3つ目: 右上から大きく走る
    listOf(
        listOf(1.00f to 0.18f, 0.78f to 0.26f, 0.84f to 0.34f, 0.60f to 0.45f, 0.66f to 0.52f, 0.45f to 0.62f),
        listOf(0.78f to 0.26f, 0.62f to 0.22f, 0.50f to 0.28f),
    ),
    // 4つ目: 枝分かれが増えて全体に広がる
    listOf(
        listOf(0.60f to 0.45f, 0.72f to 0.60f, 0.68f to 0.70f),
        listOf(0.14f to 0.60f, 0.30f to 0.52f, 0.38f to 0.56f),
        listOf(0.45f to 0.62f, 0.40f to 0.80f, 0.48f to 0.92f),
    ),
)

/** ビーカーの寸法と、金額から高さへの変換。 */
private class BeakerGeometry(width: Float, height: Float, density: Density, private val capacity: Float) {
    val stroke = with(density) { 3.dp.toPx() }
    val lip = with(density) { 6.dp.toPx() }
    // ビーカーは横方向の中央に置き、右側に目盛りの文字を書く
    val left = (width - width * 0.36f) / 2
    val right = width - left
    val rim = with(density) { 16.dp.toPx() }
    val bottom = height - stroke
    val inner = bottom - rim
    val corner = with(density) { 16.dp.toPx() }

    fun yOf(amount: Float) = bottom - inner * (amount / capacity).coerceIn(0f, 1f)
}

/**
 * 給料を支給日順にバイト先の色の層として積み、年収の壁を目盛りとして描く。
 * 水位は増減するときにアニメーションし、水面はゆっくり揺れる。
 * 壁を1つ超えるごとにひびが増え、一番上の壁に届くと割れた状態になる。
 * 長押しすると押した位置を onPeekStart で知らせ、そのまま指を動かした量を onPeekDrag で知らせる。
 */
@Composable
fun Beaker(
    layers: List<BeakerLayer>,
    walls: List<IncomeWall>,
    modifier: Modifier = Modifier,
    onPeekStart: (positionInRoot: Offset) -> Unit = {},
    onPeekDrag: (Offset) -> Unit = {},
    onPeekEnd: () -> Unit = {},
) {
    val total = layers.sumOf { it.amount }
    // 一番上の壁の少し上をビーカーの縁にする
    val capacity = (walls.maxOfOrNull { it.amount } ?: 1_000_000L) * 1.15f
    val passed = walls.count { total >= it.amount }
    // 超えた壁の数に応じて出すひびのグループ数（壁の数とグループ数が違っても最後は全部出す）
    val crackGroups = if (walls.isEmpty()) 0 else (passed * CrackGroups.size + walls.size - 1) / walls.size
    val shownTotal by animateFloatAsState(total.toFloat(), animationSpec = tween(900), label = "water")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "phase",
    )
    // 画面を開いた時点のひびは最初から出しておく（増える瞬間だけアニメーションする）
    val revealed = remember { Animatable(crackGroups.toFloat()) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(crackGroups) {
        val target = crackGroups.toFloat()
        if (target > revealed.value) {
            launch { revealed.animateTo(target, tween(600)) }
            // 割れきるときだけ大きく揺らす
            val strength = if (passed == walls.size) 8f else 4f
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    strength at 60
                    -strength at 130
                    strength * 0.75f at 200
                    -strength * 0.5f at 270
                    strength * 0.25f at 340
                },
            )
        } else {
            revealed.animateTo(target, tween(300))
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val glassColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nextColor = MaterialTheme.colorScheme.primary
    val passedColor = MaterialTheme.colorScheme.secondary
    val separatorColor = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelMedium
    val nextWall = walls.filter { total < it.amount }.minByOrNull { it.amount }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    val peekStart by rememberUpdatedState(onPeekStart)
    val peekDrag by rememberUpdatedState(onPeekDrag)
    val peekEnd by rememberUpdatedState(onPeekEnd)
    // 長押しの判定を標準（約0.4秒）より短くして、押してすぐ選択肢が出るようにする
    val baseConfiguration = LocalViewConfiguration.current
    val quickLongPress = remember(baseConfiguration) {
        object : ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long get() = 250L
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides quickLongPress) {
        Canvas(
            modifier
                .onGloballyPositioned { originInRoot = it.positionInRoot() }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            peekStart(originInRoot + position)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            peekDrag(amount)
                        },
                        onDragEnd = { peekEnd() },
                        onDragCancel = { peekEnd() },
                    )
                }
                .graphicsLayer { translationX = shake.value.dp.toPx() },
        ) {
            val g = BeakerGeometry(size.width, size.height, this, capacity)
            val left = g.left
            val right = g.right
            val rim = g.rim
            val bottom = g.bottom

            val inside = Path().apply {
                addRoundRect(
                    RoundRect(
                        left, rim, right, bottom,
                        bottomLeftCornerRadius = CornerRadius(g.corner),
                        bottomRightCornerRadius = CornerRadius(g.corner),
                    ),
                )
            }

            // 細かい目盛り（10万円ごと）
            var tick = 100_000f
            while (tick < capacity) {
                val y = g.yOf(tick)
                drawLine(glassColor.copy(alpha = 0.3f), Offset(left, y), Offset(left + 8.dp.toPx(), y), 1.dp.toPx())
                tick += 100_000f
            }

            // 水（層）
            if (shownTotal > 0f) {
                val level = g.yOf(shownTotal)
                val amplitude = 3.dp.toPx()
                val water = Path().apply {
                    moveTo(left, bottom)
                    val steps = 24
                    for (i in 0..steps) {
                        val x = left + (right - left) * i / steps
                        lineTo(x, level + amplitude * sin(phase + 2 * PI.toFloat() * i / steps))
                    }
                    lineTo(right, bottom)
                    close()
                }
                clipPath(inside) {
                    clipPath(water) {
                        var acc = 0f
                        for ((index, layer) in layers.withIndex()) {
                            val start = acc
                            acc += layer.amount
                            val isTop = acc >= shownTotal || index == layers.lastIndex
                            val y1 = g.yOf(start)
                            // 一番上の層は波の山まで塗る
                            val y2 = if (isTop) level - amplitude else g.yOf(acc)
                            drawRect(layer.color, topLeft = Offset(left, y2), size = Size(right - left, y1 - y2))
                            if (isTop) break
                            drawLine(separatorColor.copy(alpha = 0.7f), Offset(left, y2), Offset(right, y2), 1.dp.toPx())
                        }
                    }
                }
            }

            // 年収の壁の目盛り。数字が重ならないよう、下から順に詰めて置く
            var labelBottomLimit = Float.MAX_VALUE
            for (wall in walls.sortedBy { it.amount }) {
                val y = g.yOf(wall.amount.toFloat())
                val isPassed = total >= wall.amount
                val isNext = wall == nextWall
                val color = when {
                    isNext -> nextColor
                    isPassed -> passedColor
                    else -> glassColor
                }
                drawLine(
                    color.copy(alpha = 0.6f), Offset(left, y), Offset(right, y), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                drawLine(color, Offset(right - 12.dp.toPx(), y), Offset(right + 6.dp.toPx(), y), 2.dp.toPx())

                val text = (if (isPassed) "✓ " else "") + "${wall.amount / 10_000}万 ${wall.shortTitle}"
                val layout = textMeasurer.measure(
                    text,
                    labelStyle.copy(color = color, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal),
                )
                val labelTop = min(y - layout.size.height / 2f, labelBottomLimit - layout.size.height)
                drawText(layout, topLeft = Offset(right + 10.dp.toPx(), labelTop))
                labelBottomLimit = labelTop
            }

            // ガラス（上が開いた容器と、縁の出っ張り）
            val glass = Path().apply {
                moveTo(left - g.lip, rim)
                lineTo(left, rim)
                lineTo(left, bottom - g.corner)
                quadraticTo(left, bottom, left + g.corner, bottom)
                lineTo(right - g.corner, bottom)
                quadraticTo(right, bottom, right, bottom - g.corner)
                lineTo(right, rim)
                lineTo(right + g.lip, rim)
            }
            drawPath(glass, glassColor, style = Stroke(g.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // ひび。水の上でもガラスの外でも見えるよう、濃い線の上に明るい線を重ねる
            CrackGroups.forEachIndexed { index, group ->
                val progressOfGroup = (revealed.value - index).coerceIn(0f, 1f)
                if (progressOfGroup <= 0f) return@forEachIndexed
                for (points in group) {
                    val path = Path()
                    val progress = progressOfGroup * (points.size - 1)
                    points.forEachIndexed { i, (px, py) ->
                        if (i > progress + 1) return@forEachIndexed
                        var fx = px
                        var fy = py
                        if (i > progress) {
                            // 伸びている途中の線は、前の点から途中までにする
                            val (qx, qy) = points[i - 1]
                            val f = progress - (i - 1)
                            fx = qx + (px - qx) * f
                            fy = qy + (py - qy) * f
                        }
                        val x = left + (right - left) * fx
                        val y = rim + g.inner * fy
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, glassColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, separatorColor, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
