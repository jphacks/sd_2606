package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountsTest {
    @Test
    fun salaryDeductionHasTheMinimum() {
        assertEquals(740_000L, Amounts.salaryDeduction(1_400_000))
        assertEquals(660_000L, Amounts.salaryIncome(1_400_000))
        assertEquals(500_000L, Amounts.salaryDeduction(500_000)) // 年収より多くは引かない
        assertEquals(980_000L, Amounts.salaryDeduction(3_000_000)) // 300万 × 30% + 8万
    }

    @Test
    fun residentTaxStartsAbove119Man() {
        assertEquals("かかりません", Amounts.residentTax(1_190_000, student = false).value)
        // 学生でない: (66万 − 43万) × 10% − 調整控除 2,500円 = 20,500円 + 均等割 5,000円
        assertEquals("年 約25,500円", Amounts.residentTax(1_400_000, student = false).value)
        // 学生: 勤労学生控除 26万円で所得割は0、均等割だけ
        assertEquals("年 約5,000円", Amounts.residentTax(1_400_000, student = true).value)
    }

    @Test
    fun incomeTaxStartsAbove178Man() {
        assertEquals("かかりません", Amounts.incomeTax(1_780_000, student = false)!!.value)
        // (200万 − 74万 − 104万) × 5.105% = 11,231円 → 100円未満切り捨て
        assertEquals("年 約11,200円", Amounts.incomeTax(2_000_000, student = false)!!.value)
        // 合計所得132万円を超える人は目安を出さない
        assertNull(Amounts.incomeTax(2_200_000, student = false))
    }

    @Test
    fun parentDeductionFollowsTheTable() {
        assertEquals("63万円", Amounts.parentDeduction(1_360_000, 20)!!.value)
        assertEquals("63万円", Amounts.parentDeduction(1_590_000, 20)!!.value)
        assertEquals("61万円", Amounts.parentDeduction(1_600_000, 20)!!.value)
        assertEquals("なし", Amounts.parentDeduction(2_000_000, 20)!!.value)
        // 19〜22歳以外は扶養控除38万円（136万円まで）
        assertEquals("38万円", Amounts.parentDeduction(1_300_000, 18)!!.value)
        assertEquals("なし", Amounts.parentDeduction(1_400_000, 23)!!.value)
        assertNull(Amounts.parentDeduction(1_000_000, 15))
    }

    @Test
    fun amountsFollowTheirConditions() {
        val proc = ProcedureDef(
            id = "x", title = "", category = "tax", summary = "",
            amounts = listOf(
                AmountSpec("parent_deduction", card = true, conditions = listOf(Condition("supported_by", value = JsonPrimitive("parent")))),
                AmountSpec("juminzei", card = true),
            ),
        )
        val today = LocalDate.of(2026, 10, 18)
        val answers = mapOf(
            "birth_date" to Answer.Date(LocalDate.of(2006, 5, 1)),
            "annual_income" to Answer.Number(1_400_000),
        )
        // 親の扶養でなければ親の控除は出さず、住民税だけ
        assertEquals(listOf("juminzei"), Amounts.of(proc, answers + ("supported_by" to Answer.Single("none")), today).map { it.first.kind })
        assertEquals(
            listOf("parent_deduction", "juminzei"),
            Amounts.of(proc, answers + ("supported_by" to Answer.Single("parent")), today).map { it.first.kind },
        )
        // 年収が未回答なら計算しない
        assertEquals(emptyList<String>(), Amounts.of(proc, answers - "annual_income", today).map { it.first.kind })
    }

    @Test
    fun nenkinShowsMonthlyOrZeroWithTokurei() {
        assertEquals("月 17,920円", Amounts.nenkin().value)
        assertEquals("0円", Amounts.nenkinTokurei().value)
    }
}
