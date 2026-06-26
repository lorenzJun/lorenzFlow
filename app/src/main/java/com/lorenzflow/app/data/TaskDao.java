package com.lorenzflow.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {

    @Insert
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("SELECT * FROM tasks WHERE completed = 0 AND archived = 0 ORDER BY " +
           "CASE WHEN deadline > 0 THEN 0 ELSE 1 END, deadline ASC, createdAt DESC")
    List<Task> getActiveTasks();

    @Query("SELECT * FROM tasks WHERE completed = 1 AND archived = 0 ORDER BY createdAt DESC")
    List<Task> getCompletedTasks();

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task getTaskById(long id);

    @Query("SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND deadline > 0")
    List<Task> getActiveTasksWithDeadline();

    @Query("DELETE FROM tasks WHERE completed = 1 AND archived = 0")
    void deleteAllCompleted();

    @Query("SELECT * FROM tasks WHERE archived = 1 ORDER BY createdAt DESC")
    List<Task> getArchivedTasks();

    @Query("DELETE FROM tasks WHERE archived = 1")
    void deleteAllArchived();
}
