package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatContextTest {
    private val catalog = Json.decodeFromString<Catalog>(
        """
        {
          "threshold": null,
          "questions": [
            {"field": "part_time", "kind": "single", "text": "アルバイトをしていますか？", "label": "アルバイト",
             "choices": [{"label": "している", "value": "yes"}, {"label": "していない", "value": "no"}]},
            {"field": "birth_date", "kind": "date", "text": "生年月日は？", "label": "生年月日", "provides": ["age", "age_at_year_end"]},
            {"field": "annual_income", "kind": "number", "text": "年収は？", "label": "年収", "unit": "万円", "scale": 10000}
          ],
          "procedures": [
            {"id": "nenkin", "title": "国民年金", "category": "pension", "summary": "", "relations": [
              {"field": "age_at_year_end", "op": "gte", "value": 20, "flag": "required", "score": 1.0, "reason": "年末の年齢 20歳以上"}],
             "steps": [{"choice": {"field": "nenkin_pay", "title": "保険料をどうするか", "options": [
               {"value": "tokurei", "label": "学生納付特例を使う", "detail": ""},
               {"value": "pay", "label": "保険料を納める", "detail": ""}]}}]}
          ]
        }
        """,
    )
    private val today = LocalDate.of(2026, 10, 18)
    private val answers = mapOf(
        "part_time" to Answer.Single("yes"),
        "birth_date" to Answer.Date("2006-11-01"),
        "annual_income" to Answer.Number(1_600_000),
        "nenkin_pay" to Answer.Single("tokurei"),
    )

    @Test
    fun profileIsReadableAndSendsAgeInsteadOfBirthDate() {
        val evaluation = RuleEngine.evaluate(catalog.procedures, answers, null, today = today)
        val ctx = ChatContext.build(catalog, answers, evaluation, mapOf("nenkin" to "2026-10-01"), "nenkin", today)
        assertEquals(
            listOf(
                ProfileItem("アルバイト", "している"),
                ProfileItem("年齢", "19歳（今年の12月31日時点 20歳）"),
                ProfileItem("年収", "160万円"),
                ProfileItem("国民年金：保険料をどうするか", "学生納付特例を使う"),
            ),
            ctx.profile,
        )
        assertFalse(ctx.profile.any { "2006" in it.value || "11月1日" in it.value })
        assertEquals("nenkin", ctx.procedureId)
        assertEquals(listOf(ProcedureState("nenkin", "required", listOf("年末の年齢 20歳以上"), completed = true)), ctx.procedures)
    }

    @Test
    fun unansweredItemsAreNotSent() {
        val ctx = ChatContext.build(catalog, mapOf("part_time" to Answer.Single("no")), null, emptyMap(), null, today)
        assertEquals(listOf(ProfileItem("アルバイト", "していない")), ctx.profile)
        assertEquals(emptyList<ProcedureState>(), ctx.procedures)
    }

    @Test
    fun incomeBasisIsAttachedToTheIncome() {
        // 年収の「見込み / 確定」（part_of）は単独の項目にせず、年収の値に添える
        val withBasis = catalog.copy(
            questions = catalog.questions + Question(
                field = "income_basis", kind = "single", text = "見込みか確定か", label = "年収の種類", partOf = "annual_income",
                choices = listOf(Choice("見込み", "estimate"), Choice("確定", "actual")),
            ),
        )
        val ctx = ChatContext.build(withBasis, answers + ("income_basis" to Answer.Single("actual")), null, emptyMap(), null, today)
        // 確定の年収は円で書く
        assertEquals(ProfileItem("年収", "1,600,000円（確定）"), ctx.profile.first { it.label == "年収" })
        assertFalse(ctx.profile.any { it.label == "年収の種類" })
    }
}
