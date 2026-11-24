package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.RequestOptions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Modular cloud generative AI service powered by Firebase AI Logic (Gemini API),
 * grounded on retrieved local Room dictionary and phrasebook entries (RAG).
 * Always fails safely so callers can fall back immediately to offline NLU.
 */
public final class CloudGeminiService {

    private static final String TAG = "CloudGeminiService";
    private static final String PREFS_NAME = "SpeakSmartAiPrefs";
    private static final String KEY_CLOUD_ENABLED = "cloud_gemini_enabled";

    private static final String[] MODEL_CANDIDATES = {
            "gemini-3.8-flash",
            "gemini-flash-latest",
            "gemini-2.5-flash"
    };
    private static final long TIMEOUT_SECONDS = 6L;

    @Nullable
    private static volatile String lastErrorMessage = null;

    public static final class ChatTurn {
        public final boolean isUser;
        public final String text;

        public ChatTurn(boolean isUser, String text) {
            this.isUser = isUser;
            this.text = text != null ? text.trim() : "";
        }
    }

    private static final Map<String, GenerativeModelFutures> MODEL_CACHE = new ConcurrentHashMap<>();

    private CloudGeminiService() {
    }

    public static boolean isEnabled(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CLOUD_ENABLED, true);
    }

    public static void setEnabled(@NonNull Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CLOUD_ENABLED, enabled)
                .apply();
    }

    /**
     * Synchronously calls Gemini (call from a background thread only) with grounded RAG entries,
     * recent conversation history, and optional last discussed entry card.
     * Returns null on any failure or timeout so the offline engine can reply cleanly.
     */
    @Nullable
    public static String generateGroundedReply(
            @NonNull Context context,
            @NonNull String userQuery,
            @NonNull List<ChatTurn> recentHistory,
            @NonNull List<IlokanoAiEngine.EntryCard> groundedCards,
            @Nullable IlokanoAiEngine.EntryCard lastCard
    ) {
        if (!isEnabled(context) || !NetworkUtil.isOnline(context)) {
            return null;
        }

        Content promptContent = buildGroundedPrompt(userQuery, recentHistory, groundedCards, lastCard);

        for (String modelName : MODEL_CANDIDATES) {
            String reply = invokeModel(getOrCreateModel(modelName), promptContent);
            if (reply != null) {
                return reply;
            }
            if (!shouldTryNextModel(lastErrorMessage)) {
                break;
            }
        }
        return null;
    }

    private static boolean shouldTryNextModel(@Nullable String err) {
        if (err == null) {
            return false;
        }
        String lower = err.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("not found") || lower.contains("404")
                || lower.contains("model") || lower.contains("unsupported");
    }

    @Nullable
    public static String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public static void clearLastError() {
        lastErrorMessage = null;
    }

    @Nullable
    private static String invokeModel(
            @Nullable GenerativeModelFutures model,
            @NonNull Content promptContent
    ) {
        if (model == null) {
            return null;
        }
        try {
            ListenableFuture<GenerateContentResponse> future = model.generateContent(promptContent);
            GenerateContentResponse response = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response != null && response.getText() != null) {
                String text = response.getText().trim();
                if (!text.isEmpty()) {
                    lastErrorMessage = null;
                    return text;
                }
            }
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            lastErrorMessage = root.getClass().getSimpleName() + ": " + root.getMessage();
            Log.w(TAG, "Gemini call unavailable, falling back: " + lastErrorMessage, root);
        }
        return null;
    }

    @Nullable
    private static GenerativeModelFutures getOrCreateModel(@NonNull String modelName) {
        GenerativeModelFutures existing = MODEL_CACHE.get(modelName);
        if (existing != null) {
            return existing;
        }
        synchronized (CloudGeminiService.class) {
            existing = MODEL_CACHE.get(modelName);
            if (existing != null) {
                return existing;
            }
            try {
                FirebaseAI ai = FirebaseAI.getInstance(GenerativeBackend.googleAI());

                GenerationConfig config = new GenerationConfig.Builder()
                        .setTemperature(0.45f)
                        .setTopP(0.9f)
                        .setMaxOutputTokens(420)
                        .build();

                Content systemInstruction = new Content.Builder()
                        .addText(
                                "You are SpeakSmart AI, an intelligent and friendly tutor for Benguet Ilokano, "
                                        + "Tagalog, and English used by elementary learners, teachers, and tourists in Baguio City, Philippines.\n"
                                        + "Guidelines:\n"
                                        + "1. If the user is greeting you or chatting casually (e.g., 'hello?', 'hi', 'how are you?'), respond warmly and ask how you can help — do NOT turn casual greetings into unsolicited dictionary definitions.\n"
                                        + "2. When translating or teaching words/phrases, prioritize the GROUNDED DATABASE ENTRIES supplied below whenever they match.\n"
                                        + "3. Respond clearly in plain readable text (avoid raw markdown asterisks ** or heavy formatting), giving Ilokano, English, and Tagalog equivalents plus short pronunciation/grammar tips when helpful.\n"
                                        + "4. Support multi-turn follow-ups seamlessly using the recent conversation and current word/phrase focus.\n"
                                        + "5. Be honest if an uncommon Ilokano term goes beyond the curated Benguet corpus rather than inventing fake translations.\n"
                                        + "6. Keep answers concise (2-6 sentences or clean bullet points)."
                        )
                        .build();

                GenerativeModel gm = ai.generativeModel(
                        modelName,
                        config,
                        null,
                        null,
                        null,
                        systemInstruction,
                        new RequestOptions()
                );

                GenerativeModelFutures futures = GenerativeModelFutures.from(gm);
                MODEL_CACHE.put(modelName, futures);
                return futures;
            } catch (Exception e) {
                lastErrorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.w(TAG, "Unable to initialize model " + modelName + ": " + lastErrorMessage, e);
                return null;
            }
        }
    }

    @NonNull
    private static Content buildGroundedPrompt(
            @NonNull String userQuery,
            @NonNull List<ChatTurn> recentHistory,
            @NonNull List<IlokanoAiEngine.EntryCard> groundedCards,
            @Nullable IlokanoAiEngine.EntryCard lastCard
    ) {
        StringBuilder sb = new StringBuilder();

        if (!groundedCards.isEmpty()) {
            sb.append("GROUNDED SPEAKSMART CORPUS ENTRIES (3,941 curated dictionary & phrasebook entries):\n");
            int limit = Math.min(groundedCards.size(), 7);
            for (int i = 0; i < limit; i++) {
                IlokanoAiEngine.EntryCard c = groundedCards.get(i);
                sb.append(i + 1).append(". Ilokano: ").append(c.ilokano)
                        .append(" | English: ").append(c.english)
                        .append(" | Tagalog: ").append(c.tagalog);
                if (!c.partOfSpeech.isEmpty()) {
                    sb.append(" | POS: ").append(c.partOfSpeech);
                }
                if (!c.category.isEmpty()) {
                    sb.append(" | Topic: ").append(c.category);
                }
                if (!c.extraInfo.isEmpty()) {
                    sb.append(c.isPhrase ? " | Example: " : " | Note: ").append(c.extraInfo);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (lastCard != null && !lastCard.ilokano.isEmpty()) {
            sb.append("CURRENT CONVERSATION FOCUS WORD/PHRASE:\n")
                    .append("Ilokano: ").append(lastCard.ilokano)
                    .append(" | English: ").append(lastCard.english)
                    .append(" | Tagalog: ").append(lastCard.tagalog);
            if (!lastCard.extraInfo.isEmpty()) {
                sb.append(" | Note/Example: ").append(lastCard.extraInfo);
            }
            sb.append("\n\n");
        }

        if (!recentHistory.isEmpty()) {
            sb.append("RECENT CONVERSATION:\n");
            int start = Math.max(0, recentHistory.size() - 6);
            for (int i = start; i < recentHistory.size(); i++) {
                ChatTurn turn = recentHistory.get(i);
                if (turn.text.isEmpty()) continue;
                sb.append(turn.isUser ? "User: " : "Assistant: ").append(turn.text).append("\n");
            }
            sb.append("\n");
        }

        sb.append("USER MESSAGE:\n").append(userQuery);

        return new Content.Builder()
                .addText(sb.toString())
                .build();
    }
}
