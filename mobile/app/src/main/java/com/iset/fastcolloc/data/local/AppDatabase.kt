package com.iset.fastcolloc.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UserEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        private const val DATABASE_NAME = "fastcolloc_database"

        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN accountType TEXT NOT NULL DEFAULT 'USER'")
                db.execSQL("ALTER TABLE users ADD COLUMN isBlocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Preserve development profiles; remove obsolete credential columns.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE users_clean (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,lastName TEXT NOT NULL,firstName TEXT NOT NULL,email TEXT NOT NULL,role TEXT NOT NULL,governorate TEXT NOT NULL,city TEXT NOT NULL,preferredZone TEXT NOT NULL,birthDate TEXT NOT NULL,status TEXT NOT NULL,gender TEXT NOT NULL,smoker INTEGER NOT NULL,pets INTEGER NOT NULL,phone TEXT NOT NULL,preferredContact TEXT NOT NULL,photoUri TEXT,photoDescription TEXT,accountType TEXT NOT NULL DEFAULT 'USER',isBlocked INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO users_clean (id,lastName,firstName,email,role,governorate,city,preferredZone,birthDate,status,gender,smoker,pets,phone,preferredContact,photoUri,photoDescription,accountType,isBlocked) SELECT id,lastName,firstName,email,role,governorate,city,preferredZone,birthDate,status,gender,smoker,pets,phone,preferredContact,photoUri,photoDescription,accountType,isBlocked FROM users")
                db.execSQL("DROP TABLE users")
                db.execSQL("ALTER TABLE users_clean RENAME TO users")
                db.execSQL("CREATE UNIQUE INDEX index_users_email ON users(email)")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
