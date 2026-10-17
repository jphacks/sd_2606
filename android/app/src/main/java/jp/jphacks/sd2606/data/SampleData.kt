package jp.jphacks.sd2606.data

import kotlinx.serialization.json.JsonPrimitive

/** Compose の Preview 用のダミーデータ。 */
object SampleData {
    private val kokuho = ProcedureDef(
        id = "kokuho_enroll",
        title = "国民健康保険への加入",
        category = "insurance",
        summary = "勤務先の健康保険に入っていない人は、国民健康保険に加入します。",
        deadline = "退職日の翌日から14日以内",
        office = "市区町村の役所",
        steps = listOf(
            FlowStep("資格喪失証明書をもらう", "前の勤務先に発行を依頼します。", "前の勤務先", listOf("健康保険資格喪失証明書")),
            FlowStep("役所で加入手続き", "窓口で届け出ます。", "市区町村役所", listOf("マイナンバーカード")),
            FlowStep("保険料を納める", "納付書か口座振替で納めます。"),
        ),
        links = listOf(Link("厚生労働省", "https://www.mhlw.go.jp/")),
        due = Due(days = 14),
        relations = listOf(
            Relation("occupation", value = JsonPrimitive("unemployed"), flag = "conditional", score = 0.7, reason = "会社を辞めた人は、国保・任意継続・家族の扶養から選ぶため"),
            Relation("occupation", value = JsonPrimitive("employee"), flag = "unrelated", score = -1.0, reason = "会社員は勤務先の健康保険に入るため"),
        ),
    )

    private val nenkin = ProcedureDef(
        id = "kokumin_nenkin",
        title = "国民年金（第1号被保険者）の手続き",
        category = "pension",
        summary = "20〜59歳で厚生年金に入っていない人は、国民年金の手続きが必要です。",
        deadline = "該当した日から14日以内",
        relations = listOf(
            Relation(
                "occupation", value = JsonPrimitive("unemployed"), flag = "conditional", score = 0.8, reason = "無職の20〜59歳は国民年金に加入するため",
                conditions = listOf(Condition("age", "gte", JsonPrimitive(20)), Condition("age", "lt", JsonPrimitive(60))),
            ),
        ),
    )

    val catalog = Catalog(
        procedures = listOf(kokuho, nenkin),
        questions = listOf(
            Question(
                field = "occupation", kind = "multi", text = "いまの働き方は？", label = "働き方", required = true,
                choices = listOf(Choice("会社員・公務員", "employee"), Choice("フリーランス", "freelance"), Choice("無職・求職中", "unemployed")),
            ),
            Question(field = "birth_date", kind = "date", text = "生年月日は？", label = "生年月日", required = true, provides = listOf("age", "age_at_year_end")),
        ),
    )

    val answers: Map<String, Answer> = mapOf(
        "occupation" to Answer.Multi(listOf("unemployed")),
    )

    val procedure = kokuho
}
