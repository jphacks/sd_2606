package jp.jphacks.sd2606.data.meter

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Job::class, Income::class], version = 2, exportSchema = false)
abstract class MeterDatabase : RoomDatabase() {
    abstract fun meterDao(): MeterDao

    companion object {
        @Volatile private var instance: MeterDatabase? = null

        fun get(context: Context): MeterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, MeterDatabase::class.java, "meter.db")
                    // テーブルの形が変わったら移行せずに作り直す(JPhacks用)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
