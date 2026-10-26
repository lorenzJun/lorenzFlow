package com.lorenzflow.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM unit tests for the Room entities' default values and getter/setter
 * round-trips — the pure-Java parts that run without a device.
 *
 * Note: SecurityUtil cannot be unit-tested on the JVM because it needs the
 * AndroidKeyStore provider and android.util.Base64 (both absent in plain
 * JUnit runs) — its behavior is covered by the instrumented SecurityUtilTest
 * under androidTest instead.
 */
public class EntitiesTest {

    @Test
    public void taskConstructor_setsDefaults() {
        Task task = new Task();
        assertEquals("General", task.getCategory());
        assertEquals(0, task.getDeadline());
        assertFalse(task.isCompleted());
        assertFalse(task.isArchived());
        assertTrue(task.getCreatedAt() > 0);
    }

    @Test
    public void taskSetters_roundtrip() {
        Task task = new Task();
        task.setTitle("title");
        task.setDescription("desc");
        task.setDeadline(12345L);
        task.setCompleted(true);
        task.setCategory("Work");
        task.setArchived(true);

        assertEquals("title", task.getTitle());
        assertEquals("desc", task.getDescription());
        assertEquals(12345L, task.getDeadline());
        assertTrue(task.isCompleted());
        assertEquals("Work", task.getCategory());
        assertTrue(task.isArchived());
    }

    @Test
    public void noteConstructor_setsDefaults() {
        Note note = new Note();
        assertEquals(0, note.getColor());
        assertEquals(0, note.getPosition());
        assertFalse(note.isArchived());
        assertTrue(note.getTimestamp() > 0);
    }

    @Test
    public void noteSetters_roundtrip() {
        Note note = new Note();
        note.setTitle("t");
        note.setBody("b");
        note.setColor(3);
        note.setPosition(7);
        note.setArchived(true);

        assertEquals("t", note.getTitle());
        assertEquals("b", note.getBody());
        assertEquals(3, note.getColor());
        assertEquals(7, note.getPosition());
        assertTrue(note.isArchived());
    }

    @Test
    public void passwordEntryConstructor_setsDefaults() {
        PasswordEntry entry = new PasswordEntry();
        assertTrue(entry.getCreatedAt() > 0);
        assertEquals(0, entry.getId()); // autogenerate, unset until insert
    }
}
