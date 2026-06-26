package com.lorenzflow.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;
    private String body;
    private int color;          // color integer (0 = default)
    private long timestamp;
    private int position;
    private boolean archived;

    public Note() {
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public int getColor() { return color; }
    public long getTimestamp() { return timestamp; }
    public int getPosition() { return position; }
    public boolean isArchived() { return archived; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setColor(int color) { this.color = color; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setPosition(int position) { this.position = position; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
