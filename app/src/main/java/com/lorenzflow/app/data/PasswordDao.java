package com.lorenzflow.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PasswordDao {

    @Insert
    long insert(PasswordEntry entry);

    @Update
    void update(PasswordEntry entry);

    @Delete
    void delete(PasswordEntry entry);

    @Query("SELECT * FROM passwords ORDER BY title ASC")
    List<PasswordEntry> getAllPasswords();

    @Query("SELECT * FROM passwords WHERE id = :id")
    PasswordEntry getPasswordById(long id);
}
