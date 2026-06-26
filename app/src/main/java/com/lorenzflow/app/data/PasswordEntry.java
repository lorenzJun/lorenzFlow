package com.lorenzflow.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "passwords")
public class PasswordEntry {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;
    private String username;
    private String encryptedPassword;
    private String iv;
    private long createdAt;

    public PasswordEntry() {
        this.createdAt = System.currentTimeMillis();
    }

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getIv() { return iv; }
    public long getCreatedAt() { return createdAt; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setUsername(String username) { this.username = username; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public void setIv(String iv) { this.iv = iv; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
