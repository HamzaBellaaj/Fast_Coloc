package com.iset.fastcolloc

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iset.fastcolloc.data.SecureStore
import com.iset.fastcolloc.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSecurityTest {
    @Test fun keystoreCiphertextIsNotPlaintextAndSurvivesStoreRecreation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=SecureStore(context);val name="keystore-fixture";val value="fixture-session-not-a-real-token"
        try {
            store.write(name,value)
            val file=java.io.File(context.noBackupFilesDir,"fastcoloc/$name")
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(value))
            assertEquals(value,SecureStore(context).read(name))
            file.writeBytes(byteArrayOf(1,2,3))
            assertNull(store.read(name))
        } finally { store.delete(name) }
    }
    @Test fun legacyMigrationPreservesProfileAndDropsCredentialColumns() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="migration_fixture_${System.nanoTime()}"
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val sqlite=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null)
        val texts=listOf("lastName","firstName","email","passwordHash","passwordSalt","role","governorate","city","preferredZone","birthDate","status","gender","phone","preferredContact")
        sqlite.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"+texts.joinToString(",") { "$it TEXT NOT NULL" }+",smoker INTEGER NOT NULL,pets INTEGER NOT NULL,photoUri TEXT,photoDescription TEXT,accountType TEXT NOT NULL DEFAULT 'USER',isBlocked INTEGER NOT NULL DEFAULT 0)")
        sqlite.execSQL("CREATE UNIQUE INDEX index_users_email ON users(email)")
        val columns=texts+listOf("smoker","pets")
        sqlite.execSQL("INSERT INTO users ("+columns.joinToString(",")+") VALUES ("+columns.joinToString(",") { "?" }+")", (texts.map { if(it=="email") "fixture@example.invalid" else "preserved" }+listOf(0,0)).toTypedArray())
        sqlite.version=2;sqlite.close()
        val room=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_2_3).build()
        try {
            val user=room.userDao().findByEmail("fixture@example.invalid")!!
            assertEquals("preserved",user.firstName)
            room.openHelper.readableDatabase.query("PRAGMA table_info(users)").use { cursor ->
                val names=mutableListOf<String>();while(cursor.moveToNext())names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertFalse(names.contains("passwordHash"));assertFalse(names.contains("passwordSalt"));assertTrue(names.contains("firstName"))
            }
        } finally { room.close();context.deleteDatabase(name) }
    }
}
