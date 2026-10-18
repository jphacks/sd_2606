package jp.jphacks.sd2606.ui.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.jphacks.sd2606.data.meter.IncomeWall
import jp.jphacks.sd2606.data.meter.TaxEstimate
import jp.jphacks.sd2606.data.meter.estimateTaxes
import jp.jphacks.sd2606.data.meter.refundOf

/**
 * 税金などの目安の表。今の合計と、今年なら年末の見込み（このままのペース）の2列で出す。
 * 天引きされた所得税が入力されていれば、戻ってくる見込みの額も出す。
 */
@Composable
fun TaxSummary(state: MeterUiState, age: Int, next: IncomeWall?) {
    val now = estimateTaxes(state.total, age, state.workingStudent)
    val yearEndAmount = state.projection(next)?.yearEnd?.takeIf { it > state.total }
    val end = yearEndAmount?.let { estimateTaxes(it, age, state.workingStudent) }
    val columns = listOfNotNull(now, end)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        TaxRow(
            label = "税金などの目安",
            values = if (end != null) listOf("今の合計で", "年末の見込み") else listOf("この年の合計で"),
            header = true,
        )
        TaxRow("所得税", columns.map { yen(it.incomeTax) })
        TaxRow("住民税（来年6月から）", columns.map { yen(it.residentTax) })
        if (columns.any { it.healthInsurance != null }) {
            TaxRow("国保（親の扶養を外れたら）", columns.map { it.healthInsurance?.let { v -> "約${yen(v)}" } ?: "—" })
        }
        if (columns.any { it.parentTaxIncrease > 0 }) {
            TaxRow("親の税金が増える分", columns.map { if (it.parentTaxIncrease > 0) "約${yen(it.parentTaxIncrease)}" else "—" })
        }
        RefundNote(state.withheldTotal, (end ?: now))
        Text(
            "※ 給与だけ・単身の場合の簡単な目安です。" +
                (if (state.workingStudent) "勤労学生控除を申告した場合です（年収163万円以下）。" else "勤労学生控除を申告しない場合です。") +
                "国保は自治体によって大きく違います。親の分は、親の税率を所得税20%・住民税10%と仮定しています。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaxRow(label: String, values: List<String>, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = if (header) MaterialTheme.typography.titleSmall else style,
            fontWeight = if (header) FontWeight.Bold else null,
            modifier = Modifier.weight(1f),
        )
        values.forEach { value ->
            Text(value, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(92.dp))
        }
    }
}

/** 天引きされた所得税と、本来の所得税の差（戻ってくる見込み）。 */
@Composable
private fun RefundNote(withheld: Long, estimate: TaxEstimate) {
    if (withheld <= 0) {
        Text(
            "給与明細で所得税が引かれていたら、給料を追加するときに入れると、戻ってくる額の目安が出ます",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val refund = refundOf(withheld, estimate.incomeTax)
    if (refund > 0) {
        Text(
            "天引きされた所得税 ${yen(withheld)} のうち、約${yen(refund)} が年末調整か確定申告で戻ってくる見込みです",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Text(
            "天引きされた所得税 ${yen(withheld)} は、本来の所得税とほぼ同じです",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
