package jp.jphacks.sd2606.data.meter

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Job::class, Income::class], version = 3, exportSchema = false)
abstract class MeterDatabase : RoomDatabase() {
    abstract fun meterDao(): MeterDao

    companion object {
        @Volatile private var instance: MeterDatabase? = null

        /** 2→3: バイト先に「追加の一覧に出さない」印を足す。記録は消さずに残す */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): MeterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, MeterDatabase::class.java, "meter.db")
                    .addMigrations(MIGRATION_2_3)
                    // 移行を書いていない古い版からは、移行せずに作り直す(JPhacks用)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
