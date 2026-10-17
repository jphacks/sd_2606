package jp.jphacks.sd2606.data.meter

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {
    @Query("SELECT * FROM jobs ORDER BY id")
    fun jobs(): Flow<List<Job>>

    @Insert
    suspend fun insertJob(job: Job): Long

    @Update
    suspend fun updateJob(job: Job)

    /** 期間内の給料（新しい順）。from / to は "yyyy-MM-dd"（両端を含む） */
    @Query("SELECT * FROM incomes WHERE paid_on BETWEEN :from AND :to ORDER BY paid_on DESC, id DESC")
    fun incomesBetween(from: String, to: String): Flow<List<Income>>

    /** 期間内の合計。データが無ければ 0 */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM incomes WHERE paid_on BETWEEN :from AND :to")
    fun totalBetween(from: String, to: String): Flow<Long>

    @Insert
    suspend fun insertIncome(income: Income): Long

    @Update
    suspend fun updateIncome(income: Income)

    @Delete
    suspend fun deleteIncome(income: Income)
}
