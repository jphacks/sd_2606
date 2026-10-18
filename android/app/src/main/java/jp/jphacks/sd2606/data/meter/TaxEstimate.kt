package jp.jphacks.sd2606.data.meter

import kotlin.math.roundToLong

/**
 * 給与だけの1人（単身・ほかの控除なし）の、税金などの目安。2026年の収入の基準。
 * 年収の壁（IncomeWalls.kt）と同じ基準から逆算した簡易な式なので、正確な額は勤務先・税務署・自治体で確かめる。
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

object TaxRules {
    /** この年収を超えると所得税がかかる（給与所得控除と基礎控除の合計） */
    const val INCOME_TAX_FREE = 1_780_000L

    /** 所得税 5% ＋ 復興特別所得税（その 2.1%）。課税所得が 195 万円以下の範囲 */
    const val INCOME_TAX_RATE = 0.05105

    /** この年収を超えると住民税がかかる（市区町村によって少し違う） */
    const val RESIDENT_TAX_FREE = 1_190_000L

    /** 住民税の所得割の計算で年収から引く額（給与所得控除の最低額 74 万円 ＋ 住民税の基礎控除 43 万円） */
    const val RESIDENT_TAX_DEDUCTION = 1_170_000L

    /** 住民税の均等割（森林環境税を含む） */
    const val RESIDENT_TAX_PER_CAPITA = 5_000L

    /** 住民税の調整控除（人的控除の差 5 万円 × 5%） */
    const val RESIDENT_TAX_ADJUSTMENT = 2_500L

    /** 国民健康保険料の目安: 均等割・平等割の合計と、所得割の料率（自治体によって大きく違う） */
    const val HEALTH_INSURANCE_FLAT = 50_000L
    const val HEALTH_INSURANCE_RATE = 0.10

    /** 親の税率の仮定（所得税 20%・住民税 10%） */
    const val PARENT_INCOME_TAX_RATE = 0.20
    const val PARENT_RESIDENT_TAX_RATE = 0.10
}

/** 年収（1〜12月の給与）と、その年の12月31日時点の年齢から目安を出す。 */
fun estimateTaxes(annual: Long, age: Int): TaxEstimate {
    val incomeTax = floorTo100((annual - TaxRules.INCOME_TAX_FREE).coerceAtLeast(0) * TaxRules.INCOME_TAX_RATE)
    val residentTax = if (annual <= TaxRules.RESIDENT_TAX_FREE) {
        0L
    } else {
        val incomeLevy = ((annual - TaxRules.RESIDENT_TAX_DEDUCTION) * 0.10 - TaxRules.RESIDENT_TAX_ADJUSTMENT).coerceAtLeast(0.0)
        TaxRules.RESIDENT_TAX_PER_CAPITA + floorTo100(incomeLevy)
    }
    val student = age in 19..22
    val healthInsurance = if (annual < healthInsuranceLimit(age)) {
        null
    } else {
        val levy = (annual - TaxRules.RESIDENT_TAX_DEDUCTION).coerceAtLeast(0) * TaxRules.HEALTH_INSURANCE_RATE
        roundTo1000(TaxRules.HEALTH_INSURANCE_FLAT + levy)
    }
    return TaxEstimate(incomeTax, residentTax, healthInsurance, parentTaxIncrease(annual, student))
}

/**
 * 親の税金が増える分。
 * 19〜22歳: 所得税 63 万円・住民税 45 万円の控除が、年収 159 万円を超えると減り始め、197 万円でなくなる（間は直線で近似）。
 * それ以外: 年収 136 万円を超えると、所得税 38 万円・住民税 33 万円の扶養控除がなくなる。
 */
private fun parentTaxIncrease(annual: Long, student: Boolean): Long {
    val lostShare: Double
    val incomeTaxDeduction: Long
    val residentTaxDeduction: Long
    if (student) {
        lostShare = ((annual - 1_590_000L).toDouble() / (1_970_000L - 1_590_000L)).coerceIn(0.0, 1.0)
        incomeTaxDeduction = 630_000L
        residentTaxDeduction = 450_000L
    } else {
        lostShare = if (annual > 1_360_000L) 1.0 else 0.0
        incomeTaxDeduction = 380_000L
        residentTaxDeduction = 330_000L
    }
    val increase = lostShare * (incomeTaxDeduction * TaxRules.PARENT_INCOME_TAX_RATE + residentTaxDeduction * TaxRules.PARENT_RESIDENT_TAX_RATE)
    return roundTo1000(increase)
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

private fun floorTo100(value: Double): Long = (value / 100).toLong() * 100

private fun roundTo1000(value: Double): Long = (value / 1000).roundToLong() * 1000
