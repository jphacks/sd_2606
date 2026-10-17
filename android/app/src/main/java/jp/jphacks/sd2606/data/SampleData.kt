package jp.jphacks.sd2606.data

/** Compose の Preview 用のダミーデータ。 */
object SampleData {
    val recommendations = RecommendationResponse(
        items = listOf(
            Recommendation(
                procedureId = "kokuho_enroll",
                title = "国民健康保険への加入",
                category = "insurance",
                summary = "勤務先の健康保険に入っていない人は、住んでいる市区町村の国民健康保険に加入します。",
                priority = 95,
                deadline = "退職日の翌日から14日以内",
                reasons = listOf("退職して勤務先の健康保険を抜けるため"),
                personalNote = "任意継続と保険料を比べてから決めましょう。",
            ),
            Recommendation(
                procedureId = "kokumin_nenkin",
                title = "国民年金（第1号被保険者）の手続き",
                category = "pension",
                summary = "20〜59歳で厚生年金に入っていない人は、国民年金の手続きが必要です。",
                priority = 90,
                deadline = "該当した日から14日以内",
                reasons = listOf("退職して厚生年金から外れるため"),
            ),
        ),
        overallAdvice = "まずは14日以内の保険と年金の手続きを、同じ日に役所でまとめて済ませましょう。",
        aiGenerated = true,
    )

    val procedure = Procedure(
        id = "kokuho_enroll",
        title = "国民健康保険への加入",
        category = "insurance",
        summary = "勤務先の健康保険に入っていない人は、国民健康保険に加入します。",
        priority = 95,
        deadline = "退職日の翌日から14日以内",
        office = "市区町村の役所",
        steps = listOf(
            FlowStep("資格喪失証明書をもらう", "前の勤務先に発行を依頼します。", "前の勤務先", listOf("健康保険資格喪失証明書")),
            FlowStep("役所で加入手続き", "窓口で届け出ます。", "市区町村役所", listOf("マイナンバーカード")),
            FlowStep("保険料を納める", "納付書か口座振替で納めます。"),
        ),
        links = listOf(Link("厚生労働省", "https://www.mhlw.go.jp/")),
    )
}
