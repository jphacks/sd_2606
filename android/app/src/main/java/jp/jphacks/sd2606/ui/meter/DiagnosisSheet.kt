package jp.jphacks.sd2606.ui.meter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.meter.Diagnosis
import jp.jphacks.sd2606.data.meter.IncomeWall
import jp.jphacks.sd2606.data.meter.Verdict
import jp.jphacks.sd2606.data.meter.diagnose
import jp.jphacks.sd2606.data.meter.estimateTaxes
import jp.jphacks.sd2606.data.meter.takeHome

/** 「診断」ボタンで画面の下から重ねて出す。判定・お金・グラフを見せ、税金の表は折りたたむ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisSheet(state: MeterUiState, age: Int, next: IncomeWall?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        DiagnosisContent(state, age, next)
    }
}

@Composable
private fun DiagnosisContent(state: MeterUiState, age: Int, next: IncomeWall?) {
    val projection = state.projection(null)
    val yearEnd = projection?.yearEnd
    val remainingMonths = if (projection != null) 12 - state.lastPaidMonth else 0
    val d = diagnose(state.total, yearEnd, remainingMonths, age)
    var showTaxes by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Column {
            Text("診断", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (yearEnd != null) "年末の見込み ${man(yearEnd)}（今 ${man(state.total)}）" else "${state.year}年の合計 ${man(state.total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VerdictCard(d)
        MoneyTiles(d, age, isProjection = yearEnd != null)
        TakeHomeChart(d, age)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (d.verdict) {
                Verdict.OK -> MoneyLine("働き損ゾーンまで あと ", man(d.safeLimit - d.amount))
                Verdict.CAUTION -> MoneyLine("年末に ", man(d.amount - d.safeLimit + 1), " オーバーしそう", LossColor)
                Verdict.LOSS -> MoneyLine("手取りが戻るまで あと ", man(d.breakEven - d.amount), "（${man(d.breakEven)}まで）", LossColor)
                Verdict.BEYOND -> {}
            }
            // 月の上限は、まだ働き損ゾーンの手前にいるとき（◎・△）だけ出す
            d.monthlyLimit?.takeIf { d.verdict == Verdict.OK || d.verdict == Verdict.CAUTION }?.let {
                MoneyLine("12月まで 月 ", yen(it), " 以内なら入りません")
            }
            Text(
                "健康保険の扶養は、交通費を含めた「これから1年間の見込み」で判断されます。余裕をもって抑えましょう",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            TextButton(onClick = { showTaxes = !showTaxes }) {
                Text("税金などの目安", style = MaterialTheme.typography.titleSmall)
                Icon(if (showTaxes) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            AnimatedVisibility(visible = showTaxes) {
                TaxSummary(state, age, next)
            }
        }
    }
}

/** 判定を大きく1行で。 */
@Composable
private fun VerdictCard(d: Diagnosis) {
    val (mark, title, detail) = when (d.verdict) {
        Verdict.OK -> Triple("◎", "このペースなら大丈夫", "親の扶養のまま、働き損になりません")
        Verdict.CAUTION -> Triple("△", "このままだと働き損ゾーンに入りそう", "年末までに${man(d.safeLimit)}を超えそうです")
        Verdict.LOSS -> Triple("✕", "働き損ゾーンに入っています", "${man(d.breakEven)}までは、扶養の範囲で働いたときより手取りが少なくなります")
        Verdict.BEYOND -> Triple("○", "扶養を外れても損はしない範囲です", "国保や税金の手続きが必要になります")
    }
    // グラフの背景と同じ色で薄く塗る
    val container = when (d.verdict) {
        Verdict.OK -> SafeColor.copy(alpha = 0.18f)
        Verdict.CAUTION -> ParentColor.copy(alpha = 0.22f)
        Verdict.LOSS -> LossColor.copy(alpha = 0.18f)
        Verdict.BEYOND -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(mark, style = MaterialTheme.typography.displaySmall)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 手取りと引かれる額を大きな数字で並べ、その下に親の税金のことを1行で。 */
@Composable
private fun MoneyTiles(d: Diagnosis, age: Int, isProjection: Boolean) {
    val net = takeHome(d.amount, age)
    val parentIncrease = estimateTaxes(d.amount, age).parentTaxIncrease
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoneyTile(
                label = if (isProjection) "年末の手取り（見込み）" else "この年の手取り",
                value = man(net),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            MoneyTile(
                label = "税金・保険で引かれる",
                value = man(d.amount - net),
                color = if (d.amount - net > 0) LossColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        // 親の税金は自分の手取りとは別に増える。知っておいてほしいので、増える前から案内する
        if (parentIncrease > 0) {
            MoneyLine("親の税金も ", man(parentIncrease), " 増えます", ParentTextColor)
            Text(
                "親の扶養控除が減るためです（親の勤務先の年末調整で反映されます）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "${man(d.parentFrom)}を超えると、自分の手取りとは別に、親の税金も増えはじめます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoneyTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 文の中の金額だけを大きく太く、色を付けて強調する。 */
@Composable
private fun MoneyLine(before: String, money: String, after: String = "", color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, fontSize = MaterialTheme.typography.titleLarge.fontSize)) {
                append(money)
            }
            append(after)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

private val SafeColor = Color(0xFF43A047)
private val LossColor = Color(0xFFE53935)
private val ParentColor = Color(0xFFFFB300)

/** 親の税金の金額の文字色（黄色の背景の上でも読めるよう濃くする） */
private val ParentTextColor = Color(0xFFB26A00)

/**
 * 年収（横）と自分の手取り（縦）の折れ線。背景を範囲（扶養の範囲・働き損・親の税金が増える）で塗り分け、
 * 扶養を外れるところの手取りの段差と、年末の見込みのときの手取りを書き込む。
 */
@Composable
private fun TakeHomeChart(d: Diagnosis, age: Int) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val dropStyle = MaterialTheme.typography.labelLarge.copy(color = LossColor, fontWeight = FontWeight.Bold)
    val markerStyle = MaterialTheme.typography.labelLarge.copy(color = markerColor, fontWeight = FontWeight.Bold)

    // 横軸は、扶養の基準の少し手前から、親の税金が増え終わる少し先まで（見込みが外れていれば広げる）
    val xMin = (minOf(d.safeLimit - 250_000L, d.amount - 100_000L).coerceAtLeast(0L) / 50_000L) * 50_000L
    val xMax = maxOf((d.parentUntil ?: d.breakEven) + 100_000L, d.amount + 100_000L)
    // 5,000円ごとに自分の手取りを計算する
    val samples = remember(xMin, xMax, age) { (xMin..xMax step 5_000L).map { a -> a to takeHome(a, age) } }
    val yMin = samples.minOf { it.second }
    val yMax = samples.maxOf { it.second }
    val drop = takeHome(d.safeLimit - 1, age) - takeHome(d.safeLimit, age)
    val amountNet = takeHome(d.amount, age)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val top = 28.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            fun x(amount: Long) = size.width * ((amount - xMin).toFloat() / (xMax - xMin))
            fun y(value: Long) = bottom - (bottom - top) * ((value - yMin).toFloat() / (yMax - yMin).coerceAtLeast(1L))
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))

            // 背景を範囲で塗り分ける（親の税金の範囲を先に塗り、働き損を上に重ねる）
            drawRect(SafeColor.copy(alpha = 0.10f), Offset(0f, top), Size(x(d.safeLimit), bottom - top))
            val parentEnd = d.parentUntil ?: xMax
            drawRect(ParentColor.copy(alpha = 0.16f), Offset(x(d.parentFrom), top), Size(x(parentEnd) - x(d.parentFrom), bottom - top))
            drawRect(LossColor.copy(alpha = 0.14f), Offset(x(d.safeLimit), top), Size(x(d.breakEven) - x(d.safeLimit), bottom - top))
            // 扶養を外れる直前の手取りの高さ（ここまで戻るところが働き損の終わり）
            val baseY = y(takeHome(d.safeLimit - 1, age))
            drawLine(LossColor.copy(alpha = 0.6f), Offset(x(d.safeLimit), baseY), Offset(x(d.breakEven), baseY), 1.dp.toPx(), pathEffect = dash)

            val line = Path().apply {
                samples.forEachIndexed { i, (a, net) ->
                    if (i == 0) moveTo(x(a), y(net)) else lineTo(x(a), y(net))
                }
            }
            drawPath(line, lineColor, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))

            // 扶養を外れたところの手取りの段差。線と重ならないよう段差の下に離して書き、
            // 見込みの縦の点線が文字を横切るときは段差の左側に出す
            if (drop > 0) {
                val layout = textMeasurer.measure("−${man(drop)}", dropStyle)
                val labelTop = (y(takeHome(d.safeLimit, age)) + 12.dp.toPx()).coerceAtMost(bottom - layout.size.height - 2.dp.toPx())
                val rightLeft = x(d.safeLimit) + 4.dp.toPx()
                val crossesMarker = x(d.amount) in rightLeft..(rightLeft + layout.size.width)
                val labelLeft = if (crossesMarker) x(d.safeLimit) - 4.dp.toPx() - layout.size.width else rightLeft
                drawText(layout, topLeft = Offset(labelLeft.coerceAtLeast(0f), labelTop))
            }

            // 年末の見込み（今年でなければその年の合計）の位置と、そのときの手取り
            val mx = x(d.amount)
            drawLine(markerColor.copy(alpha = 0.5f), Offset(mx, top), Offset(mx, bottom), 1.dp.toPx(), pathEffect = dash)
            drawCircle(lineColor, 6.dp.toPx(), Offset(mx, y(amountNet)))
            val label = textMeasurer.measure("${(d.amount + 5_000) / 10_000}万 → 手取り ${man(amountNet)}", markerStyle)
            drawText(label, topLeft = Offset((mx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), 0f))

            // 横軸の区切り（万円）。近くて重なるときは右にずらす
            val ticks = listOfNotNull(d.safeLimit, d.parentFrom, d.breakEven, d.parentUntil).distinct().sorted()
            var minLeft = 0f
            for (tick in ticks) {
                val layout = textMeasurer.measure("${tick / 10_000}", labelStyle)
                val left = maxOf(x(tick) - layout.size.width / 2f, minLeft)
                drawText(layout, topLeft = Offset(left, bottom + 2.dp.toPx()))
                minLeft = left + layout.size.width + 6.dp.toPx()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZoneLegend(SafeColor, "扶養の範囲")
            ZoneLegend(LossColor, "働き損")
            ZoneLegend(ParentColor, "親の税金が増える")
        }
        Text("線は自分の手取り（横軸は年収・万円）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ZoneLegend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 1万円以上は「約◯万円」、それ未満は円で。 */
private fun man(value: Long): String = if (value >= 10_000) "約${(value + 5_000) / 10_000}万円" else yen(value.coerceAtLeast(0))
