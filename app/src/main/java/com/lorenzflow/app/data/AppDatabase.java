package com.lorenzflow.app.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Task.class, Note.class, PasswordEntry.class}, version = 4, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract TaskDao taskDao();
    public abstract NoteDao noteDao();
    public abstract PasswordDao passwordDao();

    // ─────────────────────────────────────────────────────────────────────────
    // Migrations — every schema change MUST have a migration listed here.
    // NEVER use fallbackToDestructiveMigration() after version 3; that wipes
    // all user data. Instead, add a new Migration block for each version bump.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * v3 → v4: Added `archived` column (0 = active, 1 = archived) to both
     * `tasks` and `notes` tables. Default 0 keeps all existing rows visible.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            database.execSQL(
                "ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        }
    };

    // ─── Add new migrations above this line ───────────────────────────────────
    // Example for the next change:
    //
    // static final Migration MIGRATION_4_5 = new Migration(4, 5) {
    //     @Override
    //     public void migrate(@NonNull SupportSQLiteDatabase database) {
    //         database.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
    //     }
    // };
    //
    // Then add MIGRATION_4_5 to addMigrations(...) in getInstance() below.
    // ─────────────────────────────────────────────────────────────────────────

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "lorenz_flow_db"
                    )
                    // Safe migrations — all user data is preserved.
                    .addMigrations(MIGRATION_3_4)
                    // Versions 1 and 2 were pre-release builds that used
                    // destructive migration themselves, so falling back on
                    // those is acceptable. Version 3+ is always migrated.
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
