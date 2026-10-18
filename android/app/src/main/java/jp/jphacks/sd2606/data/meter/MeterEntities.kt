package jp.jphacks.sd2606.data.meter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// バイト代メーターのデータ。端末の中だけに保存し、サーバーには送らない

/** バイト先。名前は「カフェ」など自由に */
@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 時給（円）。「あと何時間働けるか」の計算に使う。未設定でも可 */
    @ColumnInfo(name = "hourly_wage") val hourlyWage: Int? = null,

    /** 表示色の番号（ui/meter/JobColors.kt のパレットの位置） */
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    /** もう追加しないバイト先。給料を追加するときの一覧に出さないだけで、記録とメーターの表示は残す */
    @ColumnInfo(name = "hidden", defaultValue = "0") val hidden: Boolean = false,
)

/** 1回分の給料。年収は「働いた月」ではなく「支給日」で数えるので、支給日を持つ。 */
@Entity(
    tableName = "incomes",
    foreignKeys = [ForeignKey(entity = Job::class, parentColumns = ["id"], childColumns = ["job_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("job_id"), Index("paid_on")],
)
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "job_id") val jobId: Long,
    /** 支給日 "yyyy-MM-dd"。文字列のままでも大小比較・範囲検索ができる */
    @ColumnInfo(name = "paid_on") val paidOn: String,
    /** 支給額（円、交通費を除く） */
    val amount: Long,
    /** 天引きされた所得税（給与明細の「所得税」欄、円）。戻ってくる額の目安に使う。分からなければ 0 */
    @ColumnInfo(name = "withheld_tax", defaultValue = "0") val withheldTax: Long = 0,
)
