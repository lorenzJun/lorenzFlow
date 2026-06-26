package com.lorenzflow.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;
    private String description;
    private long deadline;      // epoch millis, 0 = no deadline
    private boolean completed;
    private long createdAt;
    private String category;
    private boolean archived; // e.g. "General", "Work", "Personal", "School", "Wellness", "Urgent"

    public Task() {
        this.createdAt = System.currentTimeMillis();
        this.category = "General"; // default category
    }

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getDeadline() { return deadline; }
    public boolean isCompleted() { return completed; }
    public long getCreatedAt() { return createdAt; }
    public String getCategory() { return category; }
    public boolean isArchived() { return archived; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setDeadline(long deadline) { this.deadline = deadline; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setCategory(String category) { this.category = category; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
