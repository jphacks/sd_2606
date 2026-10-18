package jp.jphacks.sd2606.data

/** API の値 → 画面に出す日本語。質問の選択肢の表示名はサーバーの questions.yaml から受け取る。 */
object Labels {
    val category = mapOf(
        "tax" to "税金",
        "insurance" to "保険",
        "pension" to "年金",
        "benefit" to "給付",
        "registration" to "届出",
    )
}
