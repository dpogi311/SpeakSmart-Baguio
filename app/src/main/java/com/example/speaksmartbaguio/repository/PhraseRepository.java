package com.example.speaksmartbaguio.repository;

import android.content.Context;

import com.example.speaksmartbaguio.ApiService;
import com.example.speaksmartbaguio.Phrase;
import com.example.speaksmartbaguio.dao.PhraseDao;
import com.example.speaksmartbaguio.database.DatabaseClient;
import com.example.speaksmartbaguio.entity.PhraseEntity;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.utils.VersionChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhraseRepository {

    private final PhraseDao phraseDao;
    private final ExecutorService executorService;
    private final Context context;

    public interface DatabaseCallback<T> {
        void onComplete(T result);
    }

    public interface SyncResultCallback {
        void onFinished(boolean success, String error);
    }

    public PhraseRepository(Context context) {
        this.context = context.getApplicationContext();

        phraseDao = DatabaseClient.getInstance(this.context)
                .phraseDao();

        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(PhraseEntity entity) {
        executorService.execute(() -> phraseDao.insert(entity));
    }

    public void insertAll(List<PhraseEntity> entities) {
        executorService.execute(() -> phraseDao.insertAll(entities));
    }

    public void replaceAll(List<PhraseEntity> entities) {
        executorService.execute(() -> {
            phraseDao.deleteAll();
            phraseDao.insertAll(entities);
        });
    }

    public void getAllPhrases(
            DatabaseCallback<List<PhraseEntity>> callback
    ) {
        executorService.execute(() ->
                callback.onComplete(phraseDao.getAllPhrases())
        );
    }

    public void searchPhrases(
            String query,
            DatabaseCallback<List<PhraseEntity>> callback
    ) {
        executorService.execute(() -> {
            String cleanQuery = query == null ? "" : query.trim();

            List<PhraseEntity> daoResults =
                    phraseDao.searchPhrases(cleanQuery);

            if (daoResults != null && !daoResults.isEmpty()) {
                callback.onComplete(daoResults);
                return;
            }

            // Fallback: if DAO search only checks specific columns and missed
            // ilokanoSentence (used by Daily Phrase), match against all fields.
            List<PhraseEntity> allPhrases = phraseDao.getAllPhrases();
            List<PhraseEntity> matched = new ArrayList<>();

            if (allPhrases != null && !cleanQuery.isEmpty()) {
                String lowerQuery = cleanQuery.toLowerCase(Locale.ROOT);

                for (PhraseEntity entity : allPhrases) {
                    if (entity == null) {
                        continue;
                    }

                    Phrase phrase = EntityMapper.toPhrase(entity);
                    if (phrase == null) {
                        continue;
                    }

                    if (containsIgnoreCase(phrase.getIlokanoSentence(), lowerQuery)
                            || containsIgnoreCase(phrase.getIlokanoWord(), lowerQuery)
                            || containsIgnoreCase(phrase.getEnglishSentence(), lowerQuery)
                            || containsIgnoreCase(phrase.getEnglishTranslation(), lowerQuery)
                            || containsIgnoreCase(phrase.getTagalogTranslation(), lowerQuery)) {
                        matched.add(entity);
                    }
                }
            }

            callback.onComplete(matched);
        });
    }

    private boolean containsIgnoreCase(String value, String lowerQuery) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
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
        List<PhraseEntity> allPhrases = new ArrayList<>();

        downloadPage(1, version, allPhrases, callback);
    }

    private void downloadPage(
            int page,
            long version,
            List<PhraseEntity> allPhrases,
            SyncResultCallback callback
    ) {
        ApiService.getInstance().getPhrasebook(
                page,
                100,
                "",
                new ApiService.ApiCallback<Phrase>() {
                    @Override
                    public void onSuccess(
                            List<Phrase> items,
                            boolean hasMore
                    ) {
                        try {
                            if (items != null) {
                                allPhrases.addAll(
                                        EntityMapper.toPhraseEntityList(items)
                                );
                            }

                            if (hasMore) {
                                downloadPage(
                                        page + 1,
                                        version,
                                        allPhrases,
                                        callback
                                );
                            } else {
                                saveDownloadedPhrases(
                                        version,
                                        allPhrases,
                                        callback
                                );
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

    private void saveDownloadedPhrases(
            long version,
            List<PhraseEntity> phrases,
            SyncResultCallback callback
    ) {
        executorService.execute(() -> {
            try {
                phraseDao.deleteAll();
                phraseDao.insertAll(phrases);

                // Save the version only after Room operations succeed.
                VersionChecker.savePhrasebookVersion(context, version);

                reportSuccess(callback);
            } catch (Exception error) {
                reportFailure(
                        callback,
                        error.getMessage() == null
                                ? "Could not save phrasebook data."
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
                            ? "Phrasebook synchronization failed."
                            : error
            );
        }
    }

    public void deleteAll() {
        executorService.execute(phraseDao::deleteAll);
    }

    public void close() {
        executorService.shutdown();
    }
}