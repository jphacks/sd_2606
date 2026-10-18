package jp.jphacks.sd2606.ui.meter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.meter.IncomeWall

/**
 * 今年の累計の折れ線（実線）と、このままのペースで12月まで働いたときの見込み（点線）。
 * 次の年収の壁を横線で出し、見込みがその壁を超えそうなら点線を警告の色にして、超える位置に印を付ける。
 */
@Composable
fun CumulativeChart(state: MeterUiState, walls: List<IncomeWall>) {
    val next = walls.firstOrNull { state.total < it.amount }
    val projection = state.projection(next)
    val warn = projection?.crossMonth != null
    val lastMonth = state.lastPaidMonth
    val cumulative = state.cumulative

    val textMeasurer = rememberTextMeasurer()
    val actualColor = MaterialTheme.colorScheme.primary
    val projectionColor = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val wallColor = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val markCenterColor = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val wallLabelStyle = MaterialTheme.typography.labelSmall.copy(color = wallColor)
    val projectionLabelStyle = MaterialTheme.typography.labelSmall.copy(color = projectionColor)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("今年の累計（支給月ごと）", style = MaterialTheme.typography.titleSmall)
            if (lastMonth == 0) {
                Text("まだ記録がありません", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                val bottomLabel = 18.dp.toPx()
                val top = 16.dp.toPx()
                val bottom = size.height - bottomLabel
                val maxY = maxOf(state.total, projection?.yearEnd ?: 0L, next?.amount ?: 0L).coerceAtLeast(1L) * 1.1f
                // 1〜12月を横に等間隔で並べる（月の真ん中が点の位置）
                fun x(month: Float) = size.width * (month - 0.5f) / 12f
                fun y(amount: Float) = bottom - (bottom - top) * (amount / maxY)

                // 下の線と月
                drawLine(gridColor, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
                for (month in 1..12) {
                    val layout = textMeasurer.measure("$month", labelStyle)
                    drawText(layout, topLeft = Offset(x(month.toFloat()) - layout.size.width / 2f, bottom + 2.dp.toPx()))
                }

                // 次の壁の横線
                if (next != null) {
                    val wy = y(next.amount.toFloat())
                    drawLine(
                        wallColor.copy(alpha = 0.7f), Offset(0f, wy), Offset(size.width, wy), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                    val layout = textMeasurer.measure("${next.amount / 10_000}万 ${next.shortTitle}", wallLabelStyle)
                    drawText(layout, topLeft = Offset(0f, wy - layout.size.height - 2.dp.toPx()))
                }

                // 実績（1月から最後に給料が出た月まで）
                val actual = Path()
                for (month in 1..lastMonth) {
                    val p = Offset(x(month.toFloat()), y(cumulative[month - 1].toFloat()))
                    if (month == 1) actual.moveTo(p.x, p.y) else actual.lineTo(p.x, p.y)
                }
                drawPath(actual, actualColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                for (month in 1..lastMonth) {
                    drawCircle(actualColor, 3.dp.toPx(), Offset(x(month.toFloat()), y(cumulative[month - 1].toFloat())))
                }

                // 見込み（最後に給料が出た月から12月まで、平均月収で増え続けると仮定）
                if (projection != null && lastMonth < 12) {
                    val start = Offset(x(lastMonth.toFloat()), y(state.total.toFloat()))
                    val end = Offset(x(12f), y(projection.yearEnd.toFloat()))
                    drawLine(
                        projectionColor, start, end, 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                    )
                    drawCircle(projectionColor, 3.dp.toPx(), end, style = Stroke(1.5.dp.toPx()))
                    val layout = textMeasurer.measure("約${projection.yearEnd / 10_000}万", projectionLabelStyle)
                    drawText(layout, topLeft = Offset(end.x - layout.size.width, end.y - layout.size.height - 4.dp.toPx()))

                    // 見込みが壁を超える位置に印
                    if (warn && next != null && projection.yearEnd > state.total) {
                        val t = (next.amount - state.total).toFloat() / (projection.yearEnd - state.total)
                        val cross = Offset(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)
                        drawCircle(projectionColor, 5.dp.toPx(), cross)
                        drawCircle(markCenterColor, 2.dp.toPx(), cross)
                    }
                }
            }
            // 凡例
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendLine(actualColor, dashed = false, label = "これまで")
                if (projection != null && lastMonth < 12) {
                    LegendLine(projectionColor, dashed = true, label = if (warn) "このままだと超えそう" else "このままのペース")
                }
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color, dashed: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .width(20.dp)
                .height(8.dp),
        ) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
