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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
 * 右の壁から入る大きなひびと枝分かれ、左の壁から入る小さなひび。
 */
private val Cracks = listOf(
    listOf(1.00f to 0.18f, 0.78f to 0.26f, 0.84f to 0.34f, 0.60f to 0.45f, 0.66f to 0.52f, 0.45f to 0.62f),
    listOf(0.78f to 0.26f, 0.62f to 0.22f, 0.50f to 0.28f),
    listOf(0.60f to 0.45f, 0.72f to 0.60f, 0.68f to 0.70f),
    listOf(0.00f to 0.55f, 0.14f to 0.60f, 0.10f to 0.68f, 0.26f to 0.76f),
)

/**
 * 給料を支給日順にバイト先の色の層として積み、年収の壁を目盛りとして描く。
 * 水位は増減するときにアニメーションし、水面はゆっくり揺れる。
 * 一番上の壁に届いたら、ビーカーが揺れてひびが入る（入れすぎて割れた表現）。
 */
@Composable
fun Beaker(layers: List<BeakerLayer>, walls: List<IncomeWall>, modifier: Modifier = Modifier) {
    val total = layers.sumOf { it.amount }
    val topWall = walls.maxOfOrNull { it.amount } ?: 1_000_000L
    // 一番上の壁の少し上をビーカーの縁にする
    val capacity = topWall * 1.15f
    val broken = total >= topWall
    val shownTotal by animateFloatAsState(total.toFloat(), animationSpec = tween(900), label = "water")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "phase",
    )
    // 画面を開いた時点ですでに割れていれば、ひびは最初から入れておく（割れる瞬間だけアニメーションする）
    val crack = remember { Animatable(if (broken) 1f else 0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(broken) {
        if (!broken) {
            crack.snapTo(0f)
        } else if (crack.value < 1f) {
            launch { crack.animateTo(1f, tween(600)) }
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    8f at 60
                    -8f at 130
                    6f at 200
                    -4f at 270
                    2f at 340
                },
            )
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val glassColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nextColor = MaterialTheme.colorScheme.primary
    val passedColor = MaterialTheme.colorScheme.secondary
    val separatorColor = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelMedium
    val nextWall = walls.filter { total < it.amount }.minByOrNull { it.amount }

    Canvas(modifier.graphicsLayer { translationX = shake.value.dp.toPx() }) {
        val stroke = 3.dp.toPx()
        val lip = 6.dp.toPx()
        // ビーカーは横方向の中央に置き、右側に目盛りの文字を書く
        val beakerWidth = size.width * 0.36f
        val left = (size.width - beakerWidth) / 2
        val right = left + beakerWidth
        val rim = 16.dp.toPx()
        val bottom = size.height - stroke
        val height = bottom - rim
        val corner = 16.dp.toPx()
        fun yOf(amount: Float) = bottom - height * (amount / capacity).coerceIn(0f, 1f)

        val inside = Path().apply {
            addRoundRect(
                RoundRect(
                    left, rim, right, bottom,
                    bottomLeftCornerRadius = CornerRadius(corner),
                    bottomRightCornerRadius = CornerRadius(corner),
                ),
            )
        }

        // 細かい目盛り（10万円ごと）
        var tick = 100_000f
        while (tick < capacity) {
            val y = yOf(tick)
            drawLine(glassColor.copy(alpha = 0.3f), Offset(left, y), Offset(left + 8.dp.toPx(), y), 1.dp.toPx())
            tick += 100_000f
        }

        // 水（層）
        if (shownTotal > 0f) {
            val level = yOf(shownTotal)
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
                        val y1 = yOf(start)
                        // 一番上の層は波の山まで塗る
                        val y2 = if (isTop) level - amplitude else yOf(acc)
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
            val y = yOf(wall.amount.toFloat())
            val passed = total >= wall.amount
            val isNext = wall == nextWall
            val color = when {
                isNext -> nextColor
                passed -> passedColor
                else -> glassColor
            }
            drawLine(
                color.copy(alpha = 0.6f), Offset(left, y), Offset(right, y), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            drawLine(color, Offset(right - 12.dp.toPx(), y), Offset(right + 6.dp.toPx(), y), 2.dp.toPx())

            val text = (if (passed) "✓ " else "") + "${wall.amount / 10_000}万 ${wall.shortTitle}"
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
            moveTo(left - lip, rim)
            lineTo(left, rim)
            lineTo(left, bottom - corner)
            quadraticTo(left, bottom, left + corner, bottom)
            lineTo(right - corner, bottom)
            quadraticTo(right, bottom, right, bottom - corner)
            lineTo(right, rim)
            lineTo(right + lip, rim)
        }
        drawPath(glass, glassColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ひび。水の上でもガラスの外でも見えるよう、濃い線の上に明るい線を重ねる
        if (crack.value > 0f) {
            for (points in Cracks) {
                val path = Path()
                val progress = crack.value * (points.size - 1)
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
                    val y = rim + height * fy
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, glassColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, separatorColor, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
