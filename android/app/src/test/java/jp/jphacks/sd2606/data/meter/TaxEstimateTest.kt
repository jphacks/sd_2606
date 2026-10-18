package jp.jphacks.sd2606.data.meter

import jp.jphacks.sd2606.data.Amounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaxEstimateTest {
    @Test
    fun matchesTheAmountsOnTheProcedureScreen() {
        // メーターの所得税・住民税は、手続きの画面（Amounts.kt・学生）と同じ額になる
        for (annual in listOf(800_000L, 1_190_000L, 1_400_000L, 1_630_000L, 1_800_000L, 2_000_000L)) {
            val e = estimateTaxes(annual, age = 20)
            assertEquals("住民税 $annual", Amounts.residentTaxYen(annual, student = true), e.residentTax)
            assertEquals("所得税 $annual", Amounts.incomeTaxYen(annual, student = true), e.incomeTax)
        }
    }

    @Test
    fun studentPaysOnlyThePerCapitaResidentTaxWhileWorkingStudentDeductionApplies() {
        // 140万円: 勤労学生控除で所得割は0、均等割 5,000円だけ
        assertEquals(5_000L, estimateTaxes(1_400_000, age = 20).residentTax)
        assertEquals(0L, estimateTaxes(1_190_000, age = 20).residentTax)
    }

    @Test
    fun healthInsuranceStartsAtTheDependentLimit() {
        assertNull(estimateTaxes(1_499_999, age = 20).healthInsurance)
        assertEquals(true, (estimateTaxes(1_500_000, age = 20).healthInsurance ?: 0) > 0)
        assertNull(estimateTaxes(1_299_999, age = 25).healthInsurance)
        assertEquals(true, (estimateTaxes(1_300_000, age = 25).healthInsurance ?: 0) > 0)
    }

    @Test
    fun parentTaxFollowsTheDeductionTable() {
        // 19〜22歳: 159万円までは親の控除 63万円のまま
        assertEquals(0L, estimateTaxes(1_590_000, age = 20).parentTaxIncrease)
        // 160万円: 控除が 63万 → 61万（2万減）。2万 × 20% + 2万 × 45/63 × 10% ≒ 5,429円 → 千円単位で 5,000円
        assertEquals(5_000L, estimateTaxes(1_600_000, age = 20).parentTaxIncrease)
        // それ以外の年齢: 136万円を超えると扶養控除 38万円がなくなる。38万 × 20% + 33万 × 10% = 109,000円
        assertEquals(0L, estimateTaxes(1_360_000, age = 25).parentTaxIncrease)
        assertEquals(109_000L, estimateTaxes(1_370_000, age = 25).parentTaxIncrease)
    }

    @Test
    fun refundIsWhatWasWithheldTooMuch() {
        assertEquals(18_000L, refundOf(withheld = 18_000, incomeTax = 0))
        assertEquals(0L, refundOf(withheld = 1_000, incomeTax = 5_000))
    }
}
