package com.example.speaksmartbaguio;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiService {

    private static ApiService instance;
    private final OkHttpClient client;
    private String baseUrl;
    private String apiKey;
    private final Handler mainHandler;

    private ApiService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();

        baseUrl = BuildConfig.API_BASE_URL;

        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        apiKey = BuildConfig.API_SECRET_KEY;
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService();
        }
        return instance;
    }

    public interface ApiCallback<T> {
        void onSuccess(List<T> items, boolean hasMore);
        void onError(String error);
    }

    public interface SingleResultCallback {
        void onSuccess(JSONObject item);
        void onError(String error);
    }

    // =========================================================
    // DICTIONARY
    // =========================================================

    /**
     * Normal dictionary loading and search.
     * Search behavior remains unchanged.
     */
    public void getDictionary(
            int page,
            int limit,
            String query,
            ApiCallback<Word> callback
    ) {
        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(baseUrl + "api/v1/dictionary").newBuilder();

        urlBuilder.addQueryParameter("page", String.valueOf(page));
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));

        addSearchParams(urlBuilder, query);

        executeRequest(
                urlBuilder.build(),
                page,
                limit,
                callback,
                this::parseWordList
        );
    }

    /**
     * Loads dictionary words using a prefix-style query for a selected
     * starting letter.
     *
     * IMPORTANT:
     * This sends a prefix wildcard to the API. The backend must interpret
     * a trailing wildcard as a starts-with query for this to be a true
     * server-side prefix search.
     *
     * If your backend treats all wildcards as substring searches, the
     * adapter must still check startsWith() on each returned word.
     */
    public void getDictionaryByStartingLetter(
            int page,
            int limit,
            String letter,
            ApiCallback<Word> callback
    ) {
        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(baseUrl + "api/v1/dictionary").newBuilder();

        urlBuilder.addQueryParameter("page", String.valueOf(page));
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));

        if (letter != null && !letter.trim().isEmpty()) {
            String normalizedLetter = letter.trim().substring(0, 1)
                    .toLowerCase(java.util.Locale.ROOT);

            urlBuilder.addQueryParameter(
                    "ilokanoWord",
                    normalizedLetter + "*"
            );
        }

        Log.d("DICTIONARY_PREFIX_URL", urlBuilder.build().toString());

        executeRequest(
                urlBuilder.build(),
                page,
                limit,
                callback,
                this::parseWordList
        );
    }


    public void searchDictionaryWord(
            String field,
            String value,
            ApiCallback<Word> callback
    ) {
        searchDictionaryWord(field, value, 1, 100, callback);
    }

    public void searchDictionaryWord(
            String field,
            String value,
            int page,
            int limit,
            ApiCallback<Word> callback
    ) {
        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(baseUrl + "api/v1/dictionary").newBuilder();

        urlBuilder.addQueryParameter(
                field,
                "*" + value.toLowerCase(java.util.Locale.ROOT) + "*"
        );

        urlBuilder.addQueryParameter("page", String.valueOf(page));
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));

        Log.d("DICT_URL", urlBuilder.build().toString());

        executeRequest(
                urlBuilder.build(),
                page,
                limit,
                callback,
                this::parseWordList
        );
    }


    // =========================================================
    // PHRASEBOOK
    // =========================================================

    public void getPhrasebook(
            int page,
            int limit,
            String query,
            ApiCallback<Phrase> callback
    ) {
        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(baseUrl + "api/v1/phrasebook").newBuilder();

        urlBuilder.addQueryParameter("page", String.valueOf(page));
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));

        addSearchParams(urlBuilder, query);

        executeRequest(
                urlBuilder.build(),
                page,
                limit,
                callback,
                this::parsePhraseList
        );
    }

    // =========================================================
    // TRANSLATIONS
    // =========================================================

    public void getTranslations(
            String field,
            String value,
            SingleResultCallback callback
    ) {
        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(baseUrl + "api/v1/translations").newBuilder();

        urlBuilder.addQueryParameter(field, value);
        urlBuilder.addQueryParameter("limit", "1");

        Log.d("TRANSLATOR_API", urlBuilder.build().toString());

        executeSingleRequest(urlBuilder.build(), callback);
    }

    // =========================================================
    // REQUEST EXECUTION
    // =========================================================

    private <T> void executeRequest(
            HttpUrl url,
            int page,
            int limit,
            ApiCallback<T> callback,
            JsonParser<T> parser
    ) {
        Request request = buildRequest(url);

        Log.d("FINAL_REQUEST", request.url().toString());

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                postError("Network error: " + e.getMessage(), callback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        postError(
                                "Server returned HTTP " + response.code(),
                                callback
                        );
                        return;
                    }

                    String json = response.body() != null
                            ? response.body().string()
                            : "{}";

                    Log.d("FINAL_RESPONSE", json);

                    JSONObject obj = new JSONObject(json);
                    JSONArray data = obj.optJSONArray("data");

                    List<T> items = parser.parse(data);

                    boolean hasMore = obj.optBoolean(
                            "hasMore",
                            items.size() >= limit
                    );

                    mainHandler.post(
                            () -> callback.onSuccess(items, hasMore)
                    );

                } catch (Exception e) {
                    postError("Parse error: " + e.getMessage(), callback);

                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }

    private void executeSingleRequest(
            HttpUrl url,
            SingleResultCallback callback
    ) {
        Request request = buildRequest(url);

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                postError("Network error: " + e.getMessage(), callback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        postError(
                                "Server returned HTTP " + response.code(),
                                callback
                        );
                        return;
                    }

                    String json = response.body() != null
                            ? response.body().string()
                            : "{}";

                    Log.d("TRANSLATOR_API", json);

                    JSONObject obj = new JSONObject(json);
                    JSONArray data = obj.optJSONArray("data");

                    Log.d(
                            "TRANSLATOR_API",
                            "Items = " + (data == null ? "null" : data.length())
                    );

                    if (data != null && data.length() > 0) {
                        JSONObject item = data.getJSONObject(0);
                        mainHandler.post(() -> callback.onSuccess(item));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(null));
                    }

                } catch (Exception e) {
                    postError("Parse error: " + e.getMessage(), callback);

                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Request buildRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .build();
    }

    private String getString(
            JSONObject obj,
            String primary,
            String fallback
    ) {
        if (obj.has(primary)) {
            String value = obj.optString(primary, "");

            if (!value.isEmpty()) {
                return value;
            }
        }

        return obj.optString(fallback, "");
    }

    private String getString(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                String value = obj.optString(key, "");

                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        return "";
    }

    private void addSearchParams(
            HttpUrl.Builder builder,
            String query
    ) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String normalizedQuery = query.trim().toLowerCase(
                java.util.Locale.ROOT
        );

        builder.addQueryParameter(
                "ilokanoWord",
                "*" + normalizedQuery + "*"
        );
    }

    private void postError(String message, Object callback) {
        mainHandler.post(() -> {
            if (callback instanceof ApiCallback) {
                ((ApiCallback<?>) callback).onError(message);

            } else if (callback instanceof SingleResultCallback) {
                ((SingleResultCallback) callback).onError(message);
            }
        });
    }

    // =========================================================
    // JSON PARSERS
    // =========================================================

    private interface JsonParser<T> {
        List<T> parse(JSONArray array) throws Exception;
    }

    private List<Word> parseWordList(JSONArray array) throws Exception {
        List<Word> list = new ArrayList<>();

        if (array == null) {
            return list;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);

            Word word = new Word();

            word.setId(getString(item, "id"));

            word.setEnglishTranslation(
                    getString(
                            item,
                            "english",
                            "englishTranslation",
                            "english_translation"
                    )
            );

            word.setIlokanoWord(
                    getString(
                            item,
                            "ilokano",
                            "ilokanoWord",
                            "ilokano_word"
                    )
            );

            word.setTagalogTranslation(
                    getString(
                            item,
                            "tagalog",
                            "tagalogTranslation",
                            "tagalog_translation"
                    )
            );

            word.setPartOfSpeech(
                    getString(item, "partOfSpeech", "part_of_speech")
            );

            word.setTtsUrl(getString(item, "tts_url"));
            word.setCategory(getString(item, "category"));
            word.setDefinition(getString(item, "definition"));
            word.setUpdatedAt(getString(item, "updated_at"));

            list.add(word);
        }

        return list;
    }

    private List<Phrase> parsePhraseList(JSONArray array) throws Exception {
        List<Phrase> list = new ArrayList<>();

        if (array == null) {
            return list;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);

            Phrase phrase = new Phrase();

            phrase.setId(getString(item, "id"));

            phrase.setEnglishTranslation(
                    getString(
                            item,
                            "englishTranslation",
                            "english_translation"
                    )
            );

            phrase.setIlokanoWord(
                    getString(
                            item,
                            "ilokanoWord",
                            "ilokano_word"
                    )
            );

            phrase.setTagalogTranslation(
                    getString(
                            item,
                            "tagalogTranslation",
                            "tagalog_translation"
                    )
            );

            phrase.setPartOfSpeech(
                    getString(item, "partOfSpeech", "part_of_speech")
            );

            phrase.setTtsUrl(getString(item, "tts_url"));
            phrase.setCategory(getString(item, "category"));
            phrase.setIlokanoSentence(getString(item, "ilokanoSentence"));
            phrase.setEnglishSentence(getString(item, "englishSentence"));
            phrase.setUpdatedAt(getString(item, "updated_at"));

            list.add(phrase);
        }

        return list;
    }
}
