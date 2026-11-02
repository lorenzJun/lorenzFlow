package com.lorenzflow.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for the Room migrations in {@link AppDatabase}.
 *
 * Uses the exported schemas in app/schemas (checked into git via
 * room.schemaLocation): 3.json is the pre-`archived` schema, 4.json is the
 * current one. The same package is used here so the package-private
 * migration constants are accessible.
 *
 * Run with a device/emulator attached: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String TEST_DB = "migration_test.db";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class);

    /**
     * v3 → v4 must add `archived INTEGER NOT NULL DEFAULT 0` to both
     * `tasks` and `notes`, preserving existing rows with a default of
     * 0 (active) so nothing disappears from the UI after an upgrade.
     */
    @Test
    public void migrate3to4_addsArchivedColumnDefaultingToZero() throws java.io.IOException {
        // 1. Create a database with the v3 schema and seed one row per table.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3);
        db.execSQL("INSERT INTO tasks (title, description, deadline, completed, createdAt, category)"
                + " VALUES ('Task one', 'Body of task one', 1750000000000, 0, 1, 'Work')");
        db.execSQL("INSERT INTO notes (title, body, color, timestamp, position)"
                + " VALUES ('Note one', 'Body of note one', 1, 2, 3)");
        db.close();

        // 2. Run 3 → 4 and validate the result against the current v4 schema.
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4);

        // 3. Row preserved, archived defaults to 0 (active).
        Cursor cursor = db.query("SELECT title, archived FROM tasks");
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        assertEquals("Task one", cursor.getString(0));
        assertEquals(0, cursor.getInt(1));
        cursor.close();

        cursor = db.query("SELECT title, archived FROM notes");
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        assertEquals("Note one", cursor.getString(0));
        assertEquals(0, cursor.getInt(1));
        cursor.close();

        db.close();
    }
}
