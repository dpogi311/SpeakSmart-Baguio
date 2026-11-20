
package com.example.speaksmartbaguio.repository;

import android.content.Context;

import com.example.speaksmartbaguio.ApiService;
import com.example.speaksmartbaguio.Word;
import com.example.speaksmartbaguio.dao.DictionaryDao;
import com.example.speaksmartbaguio.database.DatabaseClient;
import com.example.speaksmartbaguio.entity.DictionaryEntity;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.utils.VersionChecker;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DictionaryRepository {

    private final DictionaryDao dictionaryDao;
    private final ExecutorService executorService;
    private final Context context;
    private final ApiService apiService;

    public interface LoadCallback {
        void onLoaded(List<DictionaryEntity> result);
    }

    public interface CategoryCallback {
        void onLoaded(List<String> result);
    }

    public interface SyncResultCallback {
        void onFinished(boolean success, String error);
    }

    public interface SyncCallback {
        void onFinished();
    }

    public DictionaryRepository(Context context) {
        this.context = context.getApplicationContext();

        dictionaryDao = DatabaseClient.getInstance(this.context)
                .dictionaryDao();

        executorService = Executors.newSingleThreadExecutor();
        apiService = ApiService.getInstance();
    }

    public void insert(DictionaryEntity entity) {
        executorService.execute(() -> dictionaryDao.insert(entity));
    }

    public void insertAll(List<DictionaryEntity> entities) {
        executorService.execute(() -> dictionaryDao.insertAll(entities));
    }

    public void replaceAll(List<DictionaryEntity> entities) {
        executorService.execute(() -> {
            dictionaryDao.deleteAll();
            dictionaryDao.insertAll(entities);
        });
    }

    public void getAllWords(LoadCallback callback) {
        executorService.execute(() ->
                callback.onLoaded(dictionaryDao.getAllWords())
        );
    }

    public void getAllCategories(CategoryCallback callback) {
        executorService.execute(() ->
                callback.onLoaded(dictionaryDao.getAllCategories())
        );
    }

    public void getWordsByCategory(String category, LoadCallback callback) {
        executorService.execute(() -> {
            if (category == null
                    || category.trim().isEmpty()
                    || "All Categories".equalsIgnoreCase(category.trim())) {
                callback.onLoaded(dictionaryDao.getAllWords());
            } else {
                callback.onLoaded(dictionaryDao.getWordsByCategory(category.trim()));
            }
        });
    }

    public void searchWords(String query, LoadCallback callback) {
        executorService.execute(() ->
                callback.onLoaded(dictionaryDao.searchWords(query))
        );
    }

    public void searchWordsByCategory(String query, String category, LoadCallback callback) {
        executorService.execute(() -> {
            String cleanQuery = query == null ? "" : query.trim();
            boolean hasCategory = category != null
                    && !category.trim().isEmpty()
                    && !"All Categories".equalsIgnoreCase(category.trim());

            if (!hasCategory) {
                callback.onLoaded(dictionaryDao.searchWords(cleanQuery));
                return;
            }

            List<DictionaryEntity> daoResults =
                    dictionaryDao.searchWordsByCategory(category.trim(), cleanQuery);

            if (daoResults != null && !daoResults.isEmpty()) {
                callback.onLoaded(daoResults);
                return;
            }

            List<DictionaryEntity> categoryWords =
                    dictionaryDao.getWordsByCategory(category.trim());
            List<DictionaryEntity> matched = new ArrayList<>();

            if (categoryWords != null && !cleanQuery.isEmpty()) {
                String lowerQuery = cleanQuery.toLowerCase(java.util.Locale.ROOT);

                for (DictionaryEntity entity : categoryWords) {
                    if (entity == null) continue;
                    Word word = EntityMapper.toWord(entity);
                    if (word == null) continue;
                    if (containsIgnoreCase(word.getIlokanoWord(), lowerQuery)
                            || containsIgnoreCase(word.getEnglishTranslation(), lowerQuery)
                            || containsIgnoreCase(word.getTagalogTranslation(), lowerQuery)
                            || containsIgnoreCase(word.getDefinition(), lowerQuery)) {
                        matched.add(entity);
                    }
                }
            }

            callback.onLoaded(matched);
        });
    }

    private boolean containsIgnoreCase(String value, String lowerQuery) {
        return value != null
                && value.toLowerCase(java.util.Locale.ROOT).contains(lowerQuery);
    }

    // Backward-compatible method for existing callers.
    public void syncFromServer(
            Context context,
            long version,
            Runnable finished
    ) {
        syncFromServer(context, version, (success, error) -> {
            if (finished != null) {
                finished.run();
            }
        });
    }

    public void syncFromServer(
            Context context,
            long version,
            SyncResultCallback callback
    ) {
        FirebaseFirestore.getInstance()
                .collection("dictionary")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DictionaryEntity> allWords = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        DictionaryEntity entity = new DictionaryEntity();
                        entity.setId(doc.getId());
                        entity.setIlokanoWord(doc.getString("ilokanoWord"));
                        entity.setEnglishTranslation(doc.getString("englishTranslation"));
                        entity.setTagalogTranslation(doc.getString("tagalogTranslation"));
                        entity.setPartOfSpeech(doc.getString("partOfSpeech"));
                        entity.setDefinition(doc.getString("definition"));
                        entity.setCategory(doc.getString("category"));
                        String tts = doc.getString("ttsUrl");
                        if (tts == null) tts = doc.getString("tts_url");
                        entity.setTtsUrl(tts);
                        String updated = doc.getString("updatedAt");
                        if (updated == null) updated = doc.getString("updated_at");
                        entity.setUpdatedAt(updated);
                        allWords.add(entity);
                    }
                    if (!allWords.isEmpty()) {
                        saveDownloadedWords(version, allWords, callback);
                    } else {
                        List<DictionaryEntity> fallbackList = new ArrayList<>();
                        downloadPage(1, version, fallbackList, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    List<DictionaryEntity> fallbackList = new ArrayList<>();
                    downloadPage(1, version, fallbackList, callback);
                });
    }

    private void downloadPage(
            int page,
            long version,
            List<DictionaryEntity> allWords,
            SyncResultCallback callback
    ) {
        apiService.getDictionary(
                page,
                100,
                "",
                new ApiService.ApiCallback<Word>() {
                    @Override
                    public void onSuccess(
                            List<Word> items,
                            boolean hasMore
                    ) {
                        try {
                            if (items != null) {
                                allWords.addAll(
                                        EntityMapper.toEntityList(items)
                                );
                            }

                            if (hasMore) {
                                downloadPage(
                                        page + 1,
                                        version,
                                        allWords,
                                        callback
                                );
                            } else {
                                saveDownloadedWords(version, allWords, callback);
                            }
                        } catch (Exception error) {
                            reportFailure(callback, error.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        reportFailure(callback, error);
                    }
                }
        );
    }

    private void saveDownloadedWords(
            long version,
            List<DictionaryEntity> words,
            SyncResultCallback callback
    ) {
        executorService.execute(() -> {
            try {
                dictionaryDao.deleteAll();
                dictionaryDao.insertAll(words);

                // Save the version only after Room operations succeed.
                VersionChecker.saveDictionaryVersion(context, version);

                reportSuccess(callback);
            } catch (Exception error) {
                reportFailure(
                        callback,
                        error.getMessage() == null
                                ? "Could not save dictionary data."
                                : error.getMessage()
                );
            }
        });
    }

    private void reportSuccess(SyncResultCallback callback) {
        if (callback != null) {
            callback.onFinished(true, null);
        }
    }

    private void reportFailure(
            SyncResultCallback callback,
            String error
    ) {
        if (callback != null) {
            callback.onFinished(
                    false,
                    error == null || error.trim().isEmpty()
                            ? "Dictionary synchronization failed."
                            : error
            );
        }
    }

    public void deleteAll() {
        executorService.execute(dictionaryDao::deleteAll);
    }

    public void close() {
        executorService.shutdown();
    }
}
