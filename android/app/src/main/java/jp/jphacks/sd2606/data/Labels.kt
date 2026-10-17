package jp.jphacks.sd2606.data

/** API の値 → 画面に出す日本語。選択肢を増やすときは backend/app/schemas.py の Literal も増やす。 */
object Labels {
    val occupation = linkedMapOf(
        "employee" to "会社員・公務員",
        "part_time" to "パート・アルバイト",
        "self_employed" to "自営業",
        "freelance" to "フリーランス",
        "student" to "学生",
        "unemployed" to "無職・求職中",
        "retired" to "退職済み",
    )

    val healthInsurance = linkedMapOf(
        "employer" to "勤務先の保険",
        "national" to "国民健康保険",
        "dependent" to "家族の扶養",
        "unknown" to "わからない",
    )

    val lifeEvent = linkedMapOf(
        "new_job" to "就職・転職",
        "resign" to "退職",
        "marriage" to "結婚",
        "childbirth" to "出産",
        "move" to "引越し",
        "start_business" to "開業・独立",
        "side_job" to "副業を始めた",
        "turn_20" to "20歳になった",
        "high_medical_expense" to "医療費が多くかかった",
    )

    val category = mapOf(
        "tax" to "税金",
        "insurance" to "保険",
        "pension" to "年金",
        "benefit" to "給付",
        "registration" to "届出",
    )
}
