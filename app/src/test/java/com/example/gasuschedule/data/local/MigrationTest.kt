package com.example.gasuschedule.data.local

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Обновление приложения не должно терять задания: миграции БД проверяются на экспортированных схемах. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `с версии 1 на 2 - задания сохраняются, тип занятия пустой`() = runTest {
        helper.createDatabase(dbPath, 1).apply {
            execSQL(
                "INSERT INTO homework (id, lessonId, subject, description, dueDate, isDone, createdAt) " +
                    "VALUES ('old', '3-ТТП-26|2026-09-29|2', 'Высшая математика', 'Номера 1-5', '2026-09-30', 0, 1790000000000)",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbPath, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbPath)
            .allowMainThreadQueries()
            .build()
        val item = db.homeworkDao().observeAll().first().single().toDomain()
        assertEquals("old", item.id)
        assertEquals("Номера 1-5", item.description)
        assertEquals(LocalDate.of(2026, 9, 30), item.dueDate)
        assertNull(item.lessonType)
        db.close()
    }

    // Полный путь: MigrationTestHelper сам превращает имя в путь, и драйвер Room
    // отказывается открывать БД, если имя и путь не совпадают.
    private val dbPath: String =
        ApplicationProvider.getApplicationContext<Application>().getDatabasePath("migration-test.db").absolutePath
}
