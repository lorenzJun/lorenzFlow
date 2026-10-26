package com.lorenzflow.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Instrumented DAO tests against an in-memory Room database (never touches
 * the real lorenz_flow_db on the device).
 */
@RunWith(AndroidJUnit4.class)
public class DaoTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    // ==================== helpers ====================

    private Task task(String title, long deadline, boolean completed, boolean archived) {
        Task t = new Task();
        t.setTitle(title);
        t.setDeadline(deadline);
        t.setCompleted(completed);
        t.setArchived(archived);
        return t;
    }

    private Note note(String title, int position, boolean archived) {
        Note n = new Note();
        n.setTitle(title);
        n.setBody("body");
        n.setPosition(position);
        n.setArchived(archived);
        return n;
    }

    private PasswordEntry password(String title, String username) {
        PasswordEntry entry = new PasswordEntry();
        entry.setTitle(title);
        entry.setUsername(username);
        entry.setEncryptedPassword("cipher");
        entry.setIv("iv");
        return entry;
    }

    // ==================== TaskDao ====================

    @Test
    public void getActiveTasks_excludesCompletedAndArchived_ordersDeadlineFirst() {
        db.taskDao().insert(task("plain", 0, false, false));
        db.taskDao().insert(task("deadline", 1, false, false));
        db.taskDao().insert(task("done", 0, true, false));
        db.taskDao().insert(task("archived", 0, false, true));

        List<Task> active = db.taskDao().getActiveTasks();

        assertEquals(2, active.size());
        // Tasks with a deadline sort before tasks without one
        assertEquals("deadline", active.get(0).getTitle());
        assertEquals("plain", active.get(1).getTitle());
    }

    @Test
    public void getCompletedTasks_returnsOnlyCompleted() {
        db.taskDao().insert(task("a", 0, true, false));
        db.taskDao().insert(task("b", 0, false, false));
        db.taskDao().insert(task("c", 0, true, true)); // archived+completed stays hidden

        List<Task> completed = db.taskDao().getCompletedTasks();

        assertEquals(1, completed.size());
        assertEquals("a", completed.get(0).getTitle());
    }

    @Test
    public void deleteAllCompleted_removesOnlyCompleted() {
        long doneId = db.taskDao().insert(task("done", 0, true, false));
        db.taskDao().insert(task("active", 0, false, false));

        db.taskDao().deleteAllCompleted();

        assertNull(db.taskDao().getTaskById(doneId));
        assertEquals(1, db.taskDao().getActiveTasks().size());
    }

    @Test
    public void archivedTasks_visibleOnlyInArchive_clearable() {
        db.taskDao().insert(task("archived", 0, false, true));
        db.taskDao().insert(task("active", 0, false, false));

        assertEquals(1, db.taskDao().getArchivedTasks().size());
        assertTrue(db.taskDao().getActiveTasksWithDeadline().isEmpty());

        db.taskDao().deleteAllArchived();

        assertTrue(db.taskDao().getArchivedTasks().isEmpty());
        assertEquals(1, db.taskDao().getActiveTasks().size());
    }

    @Test
    public void getActiveTasksWithDeadline_excludesNoDeadline() {
        db.taskDao().insert(task("with-deadline", 999, false, false));
        db.taskDao().insert(task("no-deadline", 0, false, false));

        List<Task> withDeadline = db.taskDao().getActiveTasksWithDeadline();

        assertEquals(1, withDeadline.size());
        assertEquals("with-deadline", withDeadline.get(0).getTitle());
    }

    @Test
    public void updateTask_roundtrips() {
        long id = db.taskDao().insert(task("before", 0, false, false));

        Task reloaded = db.taskDao().getTaskById(id);
        reloaded.setTitle("after");
        reloaded.setCompleted(true);
        db.taskDao().update(reloaded);

        Task after = db.taskDao().getTaskById(id);
        assertEquals("after", after.getTitle());
        assertTrue(after.isCompleted());
    }

    // ==================== NoteDao ====================

    @Test
    public void getAllNotes_excludesArchived_ordersByPosition() {
        db.noteDao().insert(note("second", 2, false));
        db.noteDao().insert(note("first", 0, false));
        db.noteDao().insert(note("gone", 1, true));

        List<Note> notes = db.noteDao().getAllNotes();

        assertEquals(2, notes.size());
        assertEquals("first", notes.get(0).getTitle()); // position 0
        assertEquals("second", notes.get(1).getTitle()); // position 2
    }

    @Test
    public void getArchivedNotes_returnsOnlyArchived() {
        db.noteDao().insert(note("kept", 0, false));
        db.noteDao().insert(note("archived", 0, true));

        List<Note> archived = db.noteDao().getArchivedNotes();

        assertEquals(1, archived.size());
        assertEquals("archived", archived.get(0).getTitle());
    }

    @Test
    public void noteById_roundtrips() {
        long id = db.noteDao().insert(note("find-me", 5, false));

        Note note = db.noteDao().getNoteById(id);
        assertEquals("find-me", note.getTitle());
        assertEquals(5, note.getPosition());
        assertFalse(note.isArchived());
    }

    // ==================== PasswordDao ====================

    @Test
    public void getAllPasswords_sortedByTitle() {
        db.passwordDao().insert(password("Zebra", "z"));
        db.passwordDao().insert(password("alpha", "a"));

        List<PasswordEntry> all = db.passwordDao().getAllPasswords();

        assertEquals(2, all.size());
        // SQLite default: case-sensitive ordering puts "Zebra" before "alpha"
        assertEquals("Zebra", all.get(0).getTitle());
        assertEquals("alpha", all.get(1).getTitle());
    }

    @Test
    public void passwordById_roundtrips() {
        long id = db.passwordDao().insert(password("Gmail", "user@example.com"));

        PasswordEntry entry = db.passwordDao().getPasswordById(id);
        assertEquals("Gmail", entry.getTitle());
        assertEquals("user@example.com", entry.getUsername());
        assertEquals("cipher", entry.getEncryptedPassword());
        assertEquals("iv", entry.getIv());
    }

    @Test
    public void deletePassword_removesIt() {
        long id = db.passwordDao().insert(password("Delete me", "u"));

        db.passwordDao().delete(db.passwordDao().getPasswordById(id));

        assertNull(db.passwordDao().getPasswordById(id));
        assertTrue(db.passwordDao().getAllPasswords().isEmpty());
    }
}
