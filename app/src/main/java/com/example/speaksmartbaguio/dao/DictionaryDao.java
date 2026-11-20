package com.example.speaksmartbaguio.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.speaksmartbaguio.entity.DictionaryEntity;

import java.util.List;

@Dao
public interface DictionaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DictionaryEntity word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DictionaryEntity> words);

    @Query("SELECT * FROM dictionary ORDER BY ilokanoWord ASC")
    List<DictionaryEntity> getAllWords();

    @Query("SELECT * FROM dictionary WHERE id = :id LIMIT 1")
    DictionaryEntity getWordById(String id);

    @Query("SELECT DISTINCT category FROM dictionary WHERE category IS NOT NULL AND category != '' ORDER BY category ASC")
    List<String> getAllCategories();

    @Query("SELECT * FROM dictionary WHERE TRIM(category) = TRIM(:category) COLLATE NOCASE ORDER BY ilokanoWord ASC")
    List<DictionaryEntity> getWordsByCategory(String category);

    @Query("SELECT * FROM dictionary WHERE TRIM(category) = TRIM(:category) COLLATE NOCASE AND (" +
            "ilokanoWord LIKE '%' || :query || '%' OR " +
            "englishTranslation LIKE '%' || :query || '%' OR " +
            "tagalogTranslation LIKE '%' || :query || '%') ORDER BY ilokanoWord ASC")
    List<DictionaryEntity> searchWordsByCategory(String category, String query);

    @Query("SELECT * FROM dictionary WHERE " +
            "ilokanoWord LIKE '%' || :query || '%' OR " +
            "englishTranslation LIKE '%' || :query || '%' OR " +
            "tagalogTranslation LIKE '%' || :query || '%'")
    List<DictionaryEntity> searchWords(String query);

    @Query("DELETE FROM dictionary")
    void deleteAll();
}