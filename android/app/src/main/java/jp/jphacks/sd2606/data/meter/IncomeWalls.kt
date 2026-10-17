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
 * 金額は backend/data/procedures/nenshu_kabe.yaml・fuyou_hazureru.yaml（2026年の収入の基準）に合わせる。
 * 19〜22歳かどうかは、本来はその年の12月31日時点の年齢で決まる。
 */
fun incomeWallsFor(age: Int): List<IncomeWall> = buildList {
    add(IncomeWall(1_190_000, "住民税がかかり始める", "住民税", "市区町村によって違う目安（2025年の収入では110万円）"))
    if (age in 19..22) {
        add(
            IncomeWall(
                1_500_000, "親の健康保険の扶養から外れる", "親の健保",
                "19〜22歳は150万円未満まで。健康保険は、これから1年間の収入の見込み（交通費を含む）で判断される",
            ),
        )
        add(
            IncomeWall(
                1_590_000, "親の税金の控除が減り始める", "親の控除",
                "特定親族特別控除が満額なのは159万円まで。197万円を超えると控除がなくなる",
            ),
        )
    } else {
        add(
            IncomeWall(
                1_300_000, "親の健康保険の扶養から外れる", "親の健保",
                "これから1年間の収入の見込み（交通費を含む）が130万円以上になると外れる",
            ),
        )
        add(IncomeWall(1_360_000, "親の扶養控除の対象から外れる", "扶養控除", "親の税金が増える"))
    }
    add(IncomeWall(1_780_000, "自分に所得税がかかり始める", "所得税"))
}.sortedBy { it.amount }
