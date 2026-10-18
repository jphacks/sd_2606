package jp.jphacks.sd2606.data.meter

import jp.jphacks.sd2606.data.Amounts
import kotlin.math.roundToLong

/**
 * 給与だけの学生1人の、税金などの目安（2026年の収入）。
 * 所得税・住民税・親の控除は、手続きの画面と同じ計算（data/Amounts.kt。勤労学生控除を受ける前提）を使う。
 * 国保の保険料と親の税率は Amounts.kt に無いので、ここで仮定する（TaxRules）。
 */
data class TaxEstimate(
    /** 本人の所得税（復興特別所得税を含む） */
    val incomeTax: Long,
    /** 本人の住民税（翌年の6月から納める） */
    val residentTax: Long,
    /** 親の健康保険の扶養から外れて国民健康保険に入るときの保険料（1年分）。外れないなら null */
    val healthInsurance: Long?,
    /** 親の税金が増える分（親の扶養に入っている場合）。増えないなら 0 */
    val parentTaxIncrease: Long,
)

/** Amounts.kt に無い仮定。 */
object TaxRules {
    /** 国民健康保険料の目安: 均等割・平等割の合計と、所得割の料率（自治体によって大きく違う） */
    const val HEALTH_INSURANCE_FLAT = 50_000L
    const val HEALTH_INSURANCE_RATE = 0.10

    /** 親の税率の仮定（所得税 20%・住民税 10%） */
    const val PARENT_INCOME_TAX_RATE = 0.20
    const val PARENT_RESIDENT_TAX_RATE = 0.10

    /** 親の住民税の控除は、所得税の控除に対してこの割合と仮定する（特定扶養 45万/63万、一般の扶養 33万/38万） */
    const val PARENT_RESIDENT_SHARE_STUDENT = 45.0 / 63.0
    const val PARENT_RESIDENT_SHARE_OTHER = 33.0 / 38.0
}

/** 年収（1〜12月の給与）と、その年の12月31日時点の年齢から目安を出す。 */
fun estimateTaxes(annual: Long, age: Int): TaxEstimate {
    val incomeTax = Amounts.incomeTaxYen(annual, student = true) ?: highIncomeTax(annual)
    val residentTax = Amounts.residentTaxYen(annual, student = true)
    val healthInsurance = if (annual < healthInsuranceLimit(age)) {
        null
    } else {
        // 所得割は、給与所得から住民税の基礎控除を引いた額にかかるとする
        val base = (Amounts.salaryIncome(annual) - Amounts.RESIDENT_TAX_BASIC).coerceAtLeast(0)
        roundTo1000(TaxRules.HEALTH_INSURANCE_FLAT + base * TaxRules.HEALTH_INSURANCE_RATE)
    }
    return TaxEstimate(incomeTax, residentTax, healthInsurance, parentTaxIncrease(annual, age))
}

/**
 * 親の税金が増える分。親の控除（所得税）が、収入が無いときと比べてどれだけ減るかを Amounts.kt で出し、
 * 住民税の控除も同じ割合で減ると仮定して、親の税率を掛ける。
 */
private fun parentTaxIncrease(annual: Long, age: Int): Long {
    val full = Amounts.parentDeductionYen(0, age) ?: return 0
    val lost = full - (Amounts.parentDeductionYen(annual, age) ?: 0)
    if (lost <= 0) return 0
    val residentShare = if (age in 19..22) TaxRules.PARENT_RESIDENT_SHARE_STUDENT else TaxRules.PARENT_RESIDENT_SHARE_OTHER
    return roundTo1000(lost * TaxRules.PARENT_INCOME_TAX_RATE + lost * residentShare * TaxRules.PARENT_RESIDENT_TAX_RATE)
}

/**
 * Amounts.kt が目安を出さない、合計所得132万円（年収206万円）を超える場合の所得税。
 * 基礎控除が小さくなる分は含めない簡易な目安（実際より少なめに出る）。
 */
private fun highIncomeTax(annual: Long): Long {
    val taxable = (Amounts.salaryIncome(annual) - Amounts.INCOME_TAX_BASIC).coerceAtLeast(0) / 1_000 * 1_000
    return taxable * 5_105 / 100_000 / 100 * 100
}

/** 健康保険の扶養から外れる年収。130 万円以上（19〜22歳は 150 万円以上）で外れる */
fun healthInsuranceLimit(age: Int): Long = if (age in 19..22) 1_500_000L else 1_300_000L

/** 自分の手取り（年収 − 所得税 − 住民税 − 国保）。 */
fun takeHome(annual: Long, age: Int): Long {
    val e = estimateTaxes(annual, age)
    return annual - e.incomeTax - e.residentTax - (e.healthInsurance ?: 0)
}

/** 天引きされた所得税のうち、戻ってくる見込みの額（引かれすぎた分）。 */
fun refundOf(withheld: Long, incomeTax: Long): Long = (withheld - incomeTax).coerceAtLeast(0)

private fun roundTo1000(value: Double): Long = (value / 1000).roundToLong() * 1000
