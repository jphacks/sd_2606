package jp.jphacks.sd2606.data.meter

/** 年収の壁。金額は目安。 */
data class IncomeWall(
    val amount: Long,
    val title: String,
    /** ビーカーの目盛りに添える短い名前 */
    val shortTitle: String,
    val detail: String = "",
)

/**
 * 年齢に応じた年収の壁（1〜12月の給与収入だけで、ほかに収入が無い前提）。
 * 19〜22歳は、その年の12月31日時点の年齢で判定される。
 */
fun incomeWallsFor(age: Int): List<IncomeWall> = buildList {
    add(IncomeWall(1_100_000, "住民税がかかり始める", "住民税", "金額は自治体によって少し違う"))
    if (age in 19..22) {
        add(IncomeWall(1_500_000, "親の扶養から外れ始める", "親の扶養", "親の税金の控除が減り始め、親の健康保険の扶養からも外れる"))
    } else {
        add(IncomeWall(1_230_000, "親の扶養控除の対象から外れる", "扶養控除", "親の税金が増える"))
        add(IncomeWall(1_300_000, "親の健康保険の扶養から外れる", "親の健保", "自分で国民健康保険・国民年金に入る必要が出る"))
    }
    add(IncomeWall(1_600_000, "自分に所得税がかかり始める", "所得税"))
}
