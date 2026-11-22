package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.speaksmartbaguio.BuildConfig;
import com.example.speaksmartbaguio.database.AppDatabase;
import com.example.speaksmartbaguio.database.DatabaseClient;
import com.example.speaksmartbaguio.entity.DictionaryEntity;
import com.example.speaksmartbaguio.entity.PhraseEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class IlokanoAiEngine {

    private static final String TIMING_TAG = "AiTiming";
    private static final String VERSION_PREFS = "app_versions";

    public static class EntryCard {
        public final String ilokano;
        public final String english;
        public final String tagalog;
        public final String partOfSpeech;
        public final String category;
        public final String extraInfo;
        public final String ttsUrl;
        public final boolean isPhrase;

        public EntryCard(String ilokano, String english, String tagalog,
                         String partOfSpeech, String category, String extraInfo,
                         String ttsUrl, boolean isPhrase) {
            this.ilokano = safe(ilokano);
            this.english = safe(english);
            this.tagalog = safe(tagalog);
            this.partOfSpeech = safe(partOfSpeech);
            this.category = safe(category);
            this.extraInfo = safe(extraInfo);
            this.ttsUrl = safe(ttsUrl);
            this.isPhrase = isPhrase;
        }
    }

    public static class AiReply {
        public final String message;
        @Nullable
        public final EntryCard card;
        public final boolean fromCloud;

        public AiReply(String message, @Nullable EntryCard card, boolean fromCloud) {
            this.message = message != null ? message : "";
            this.card = card;
            this.fromCloud = fromCloud;
        }
    }

    public interface ReplyCallback {
        void onReply(AiReply reply);
    }

    private static final class IndexedEntry {
        final EntryCard card;
        final String normIlo;
        final String normEn;
        final String normTl;
        final String lowerCat;
        final String lowerEn;
        final boolean isPhrase;
        final int wordCountIlo;

        IndexedEntry(EntryCard card, boolean isPhrase) {
            this.card = card;
            this.isPhrase = isPhrase;
            this.normIlo = normalize(card.ilokano);
            this.normEn = normalize(card.english);
            this.normTl = normalize(card.tagalog);
            this.lowerCat = card.category.toLowerCase(Locale.ROOT);
            this.lowerEn = card.english.toLowerCase(Locale.ROOT);
            this.wordCountIlo = countWords(this.normIlo);
        }
    }

    private static final class ScoredCard {
        final EntryCard card;
        final double score;

        ScoredCard(EntryCard card, double score) {
            this.card = card;
            this.score = score;
        }
    }

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    /**
     * Set to true only after the server endpoint POST /api/v1/assistant is deployed.
     * While false, all answers come instantly from the offline dictionary and phrasebook.
     */
    private static final boolean ENABLE_CLOUD_AI = false;

    // Single background thread serializes index loading and query evaluation without locking.
    private static final ExecutorService AI_EXECUTOR = Executors.newSingleThreadExecutor();
    private static List<IndexedEntry> sharedIndex = new ArrayList<>();
    private static List<IndexedEntry> sharedPhrases = new ArrayList<>();
    private static List<EntryCard> sharedQuizPool = new ArrayList<>();
    private static String sharedVersionKey = "";

    private static final Set<String> SIMPLE_GREETINGS = new HashSet<>(Arrays.asList(
            "hi", "hello", "hey", "kumusta", "naimbag",
            "good morning", "good afternoon", "good evening"
    ));

    private static final List<String> BAGUIO_TIP_KEYWORDS = Arrays.asList(
            "please", "thank", "excuse", "where", "how much", "stop"
    );

    private static final String[] QUESTION_PREFIXES = {
            "how ", "what ", "what's ", "whats ", "where ", "when ", "why ", "who ",
            "translate ", "meaning of ", "tell me ", "give me ", "show me ", "can you ",
            "teach me ", "quiz", "help", "baguio", "hello", "hi", "hey"
    };

    private static final int QUIZ_LENGTH = 5;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    @Nullable
    private OkHttpClient httpClient;

    // Quiz state is accessed only on AI_EXECUTOR (and reset cleanly in resetSession).
    @Nullable
    private volatile EntryCard pendingQuizCard = null;
    private int quizQuestionNumber = 0;
    private int quizCorrect = 0;
    private int quizStreak = 0;
    private int quizBestStreak = 0;
    private final Set<String> quizAsked = new HashSet<>();

    public IlokanoAiEngine(Context context) {
        this.appContext = context.getApplicationContext();
        preloadData(null);
    }

    public void preloadData(@Nullable Runnable onReady) {
        AI_EXECUTOR.execute(() -> {
            ensureIndexLoaded();
            if (onReady != null) {
                mainHandler.post(onReady);
            }
        });
    }

    public void resetSession() {
        AI_EXECUTOR.execute(this::resetQuizState);
    }

    public void ask(String userInput, @NonNull ReplyCallback callback) {
        final String trimmed = userInput != null ? userInput.trim() : "";
        if (trimmed.isEmpty()) {
            mainHandler.post(() -> callback.onReply(new AiReply(
                    "Type or speak a question — for example, \"How do you say thank you?\", \"Travel phrases\", or \"Quiz me!\"",
                    null,
                    false
            )));
            return;
        }

        final long startNs = System.nanoTime();
        AI_EXECUTOR.execute(() -> {
            ensureIndexLoaded();
            processQuery(trimmed, callback, startNs);
        });
    }

    private void ensureIndexLoaded() {
        SharedPreferences prefs = appContext.getSharedPreferences(VERSION_PREFS, Context.MODE_PRIVATE);
        long dictVer = prefs.getLong("dictionaryVersion", 0L);
        long phraseVer = prefs.getLong("phrasebookVersion", 0L);
        String versionKey = dictVer + ":" + phraseVer;

        if (!sharedIndex.isEmpty() && versionKey.equals(sharedVersionKey)) {
            return;
        }

        long loadStartNs = System.nanoTime();
        AppDatabase db = DatabaseClient.getInstance(appContext);
        List<PhraseEntity> phraseEntities = db.phraseDao().getAllPhrases();
        List<DictionaryEntity> wordEntities = db.dictionaryDao().getAllWords();

        int estimatedSize = (phraseEntities != null ? phraseEntities.size() : 0)
                + (wordEntities != null ? wordEntities.size() : 0);

        List<IndexedEntry> indexList = new ArrayList<>(estimatedSize);
        List<IndexedEntry> phraseList = new ArrayList<>(phraseEntities != null ? phraseEntities.size() : 0);
        List<EntryCard> quizList = new ArrayList<>();

        if (phraseEntities != null) {
            for (PhraseEntity p : phraseEntities) {
                if (p == null) continue;
                String extra = !safe(p.getIlokanoSentence()).isEmpty()
                        ? p.getIlokanoSentence()
                        : p.getEnglishSentence();
                EntryCard card = new EntryCard(
                        p.getIlokanoWord(),
                        p.getEnglishTranslation(),
                        p.getTagalogTranslation(),
                        p.getPartOfSpeech(),
                        p.getCategory(),
                        extra,
                        p.getTtsUrl(),
                        true
                );
                IndexedEntry entry = new IndexedEntry(card, true);
                indexList.add(entry);
                phraseList.add(entry);
                if (entry.wordCountIlo >= 1 && entry.wordCountIlo <= 3
                        && !card.ilokano.isEmpty() && !card.english.isEmpty()) {
                    quizList.add(card);
                }
            }
        }

        if (wordEntities != null) {
            for (DictionaryEntity w : wordEntities) {
                if (w == null) continue;
                EntryCard card = new EntryCard(
                        w.getIlokanoWord(),
                        w.getEnglishTranslation(),
                        w.getTagalogTranslation(),
                        w.getPartOfSpeech(),
                        w.getCategory(),
                        w.getDefinition(),
                        w.getTtsUrl(),
                        false
                );
                IndexedEntry entry = new IndexedEntry(card, false);
                indexList.add(entry);
                if (entry.wordCountIlo >= 1 && entry.wordCountIlo <= 3
                        && !card.ilokano.isEmpty() && !card.english.isEmpty()) {
                    quizList.add(card);
                }
            }
        }

        sharedIndex = indexList;
        sharedPhrases = phraseList;
        sharedQuizPool = quizList;
        sharedVersionKey = versionKey;

        long loadMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - loadStartNs);
        Log.d(TIMING_TAG, "Indexed " + indexList.size() + " entries (" + quizList.size()
                + " quiz candidates) in " + loadMs + " ms [v=" + versionKey + "]");
    }

    private void processQuery(String query, ReplyCallback callback, long startNs) {
        String lower = query.toLowerCase(Locale.ROOT).trim();

        // 1. Active quiz grading, stop commands, or mid-quiz question exit
        if (pendingQuizCard != null) {
            if (isQuizStopCommand(lower)) {
                int answered = Math.max(0, quizQuestionNumber - 1);
                AiReply stopReply = new AiReply(buildQuizSummary(answered, false), null, false);
                resetQuizState();
                deliverReply(query, stopReply, callback, startNs);
                return;
            }
            if (!looksLikeQuestionOrCommand(lower)) {
                AiReply quizFeedback = handleQuizAnswer(query);
                deliverReply(query, quizFeedback, callback, startNs);
                return;
            }
            if (!containsAny(lower, "quiz", "test me", "practice", "challenge")) {
                resetQuizState();
            }
        }

        // 2. Start quiz
        if (containsAny(lower, "quiz", "test me", "practice", "challenge")) {
            AiReply quizPrompt = buildQuizQuestion();
            deliverReply(query, quizPrompt, callback, startNs);
            return;
        }

        // 3. Fast intents before running any full search (when local AI is active)
        if (!ENABLE_CLOUD_AI || isSimpleGreeting(lower) || !NetworkUtil.isOnline(appContext)) {
            AiReply localReply = buildLocalReply(query, lower, null);
            deliverReply(query, localReply, callback, startNs);
            return;
        }

        // 4. Optional Cloud AI path (grounded by a single local search)
        List<ScoredCard> topMatches = findBestMatches(extractSearchPhrase(query), 4);
        if (topMatches.isEmpty()) {
            topMatches = findBestMatches(query, 4);
        }
        final List<ScoredCard> contextMatches = topMatches;
        final EntryCard primaryCard = contextMatches.isEmpty() ? null : contextMatches.get(0).card;

        tryCloudAssistant(query, contextMatches, cloudReply -> {
            if (cloudReply != null) {
                deliverReply(query, new AiReply(cloudReply, primaryCard, true), callback, startNs);
            } else {
                AiReply fallback = buildLocalReply(query, lower, contextMatches);
                deliverReply(query, fallback, callback, startNs);
            }
        });
    }

    private void deliverReply(String query, AiReply reply, ReplyCallback callback, long startNs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        Log.d(TIMING_TAG, "Query \"" + query + "\" answered in " + elapsedMs + " ms");
        mainHandler.post(() -> callback.onReply(reply));
    }

    private synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .writeTimeout(4, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    private void tryCloudAssistant(String query, List<ScoredCard> contextMatches,
                                   java.util.function.Consumer<String> onResult) {
        try {
            String baseUrl = BuildConfig.API_BASE_URL;
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                onResult.accept(null);
                return;
            }
            String url = baseUrl.endsWith("/") ? baseUrl + "api/v1/assistant" : baseUrl + "/api/v1/assistant";

            JSONObject payload = new JSONObject();
            payload.put("message", query);

            JSONArray contextArray = new JSONArray();
            for (ScoredCard sc : contextMatches) {
                JSONObject item = new JSONObject();
                item.put("ilokano", sc.card.ilokano);
                item.put("english", sc.card.english);
                item.put("tagalog", sc.card.tagalog);
                item.put("category", sc.card.category);
                item.put("partOfSpeech", sc.card.partOfSpeech);
                contextArray.put(item);
            }
            payload.put("context", contextArray);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json");

            if (BuildConfig.API_SECRET_KEY != null && !BuildConfig.API_SECRET_KEY.trim().isEmpty()) {
                builder.addHeader("x-api-key", BuildConfig.API_SECRET_KEY);
            }

            try (Response response = getHttpClient().newCall(builder.build()).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());
                    String reply = json.optString("reply", "").trim();
                    if (!reply.isEmpty()) {
                        onResult.accept(reply);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // Endpoint optional; fall back cleanly to local smart assistant.
        }
        onResult.accept(null);
    }

    private AiReply buildLocalReply(String rawQuery, String lower, @Nullable List<ScoredCard> precomputedMatches) {
        // Simple greetings
        if (isSimpleGreeting(lower)) {
            return new AiReply(
                    "Naimbag nga aldaw! (Good day!)\n\n"
                            + "I'm SpeakSmart AI, your Baguio Ilokano companion. You can ask me:\n"
                            + "• \"How do you say thank you in Ilokano?\"\n"
                            + "• \"Travel phrases\" or \"Food & Dining\"\n"
                            + "• \"Baguio tips\" for local etiquette\n"
                            + "• \"Quiz me!\" to practice vocabulary",
                    null,
                    false
            );
        }

        // Help
        if (lower.equals("help") || lower.contains("what can you do")) {
            return new AiReply(
                    "Here is how I can help you learn Ilokano:\n\n"
                            + "1. Translate & Explain: Ask \"How do you say [word]?\" or \"What does [Ilokano word] mean?\"\n"
                            + "2. Browse Topics: Ask for \"Greetings\", \"Travel phrases\", \"Food\", \"Market\", or \"Emergency\".\n"
                            + "3. Listen: Tap the speaker button on any card to hear pronunciation.\n"
                            + "4. Practice: Type \"Quiz me!\" for a quick Ilokano challenge.",
                    null,
                    false
            );
        }

        // Random / Word of the day
        if (containsAny(lower, "random", "word of the day", "teach me", "surprise me")) {
            EntryCard randomCard = pickRandomCard();
            if (randomCard != null) {
                return new AiReply(
                        "Here is an Ilokano " + (randomCard.isPhrase ? "phrase" : "word")
                                + " to practice today! Tap the speaker icon to hear it:",
                        randomCard,
                        false
                );
            }
        }

        // Thanks
        if (containsAny(lower, "thank you", "thanks", "agyamanak", "salamat")) {
            EntryCard thankCard = findExactOrBest("thank you");
            return new AiReply(
                    "Awan aniaman! (You're welcome!)\nIn Ilokano, you can say \"Agyamanak\" (Thank you) or \"Agyamanak unay\" (Thank you very much).",
                    thankCard,
                    false
            );
        }

        // Baguio City cultural & communication tips
        if (containsAny(lower, "baguio", "jeepney", "etiquette", "culture", "tips")) {
            EntryCard tipCard = findByTopicKeywords(BAGUIO_TIP_KEYWORDS);
            StringBuilder sb = new StringBuilder();
            sb.append("Baguio Local Communication Tips:\n\n");
            sb.append("• Politeness: Add \"Apo\" when speaking respectfully to elders, vendors, or drivers (e.g., \"Agyamanak, Apo\").\n");
            sb.append("• Jeepneys: Say \"Para po\" or \"Agsardeng ditoy\" when you want to get off.\n");
            sb.append("• Public Market: Ask \"Mano daytoy?\" for \"How much is this?\" with a friendly smile.\n");
            if (tipCard != null) {
                sb.append("\nHere is a handy phrase from your phrasebook:");
            }
            return new AiReply(sb.toString(), tipCard, false);
        }

        // Topic / Category browsing (matches standardized 30 categories)
        String matchedCategory = detectCategoryIntent(lower);
        if (matchedCategory != null) {
            List<EntryCard> categoryCards = findByCategoryOrTopic(matchedCategory, 4);
            if (!categoryCards.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Here are useful ").append(matchedCategory).append(" expressions in Ilokano:\n");
                for (int i = 1; i < categoryCards.size(); i++) {
                    EntryCard extra = categoryCards.get(i);
                    sb.append("\n• ").append(extra.ilokano).append(" — ").append(extra.english);
                }
                sb.append("\n\nTap the speaker on the card below to hear the top phrase:");
                return new AiReply(sb.toString(), categoryCards.get(0), false);
            }
        }

        // Translation / "How do you say X" / General lookup — run single search on extracted phrase
        String cleanedTarget = extractSearchPhrase(rawQuery);
        List<ScoredCard> targetMatches = findBestMatches(cleanedTarget, 4);
        if (targetMatches.isEmpty() && !cleanedTarget.equalsIgnoreCase(rawQuery)) {
            targetMatches = precomputedMatches != null ? precomputedMatches : findBestMatches(rawQuery, 4);
        }

        if (!targetMatches.isEmpty()) {
            ScoredCard best = targetMatches.get(0);
            StringBuilder sb = new StringBuilder();
            if (best.score >= 0.85) {
                sb.append("In Ilokano, \"").append(best.card.english).append("\" is \"")
                        .append(best.card.ilokano).append("\".");
            } else {
                sb.append("Here is the closest Ilokano match I found for \"")
                        .append(cleanedTarget).append("\":");
            }

            if (targetMatches.size() > 1) {
                sb.append("\n\nRelated matches:");
                for (int i = 1; i < Math.min(targetMatches.size(), 4); i++) {
                    EntryCard c = targetMatches.get(i).card;
                    sb.append("\n• ").append(c.ilokano).append(" (").append(c.english).append(")");
                }
            }
            return new AiReply(sb.toString(), best.card, false);
        }

        return new AiReply(
                "I couldn't find \"" + cleanedTarget + "\" in the offline dictionary or phrasebook yet.\n\n"
                        + "Try asking for a simpler word (like \"water\", \"eat\", \"market\", \"morning\") or tap one of the quick prompts below!",
                null,
                false
        );
    }

    // ---------------------------------------------------------
    // QUIZ MODE (5-question rounds with O(1) selection)
    // ---------------------------------------------------------

    private void resetQuizState() {
        pendingQuizCard = null;
        quizQuestionNumber = 0;
        quizCorrect = 0;
        quizStreak = 0;
        quizBestStreak = 0;
        quizAsked.clear();
    }

    private AiReply buildQuizQuestion() {
        resetQuizState();
        EntryCard card = pickQuizCard();
        if (card == null) {
            return new AiReply(
                    "Your dictionary is still syncing. Please try again in a moment!",
                    null,
                    false
            );
        }
        pendingQuizCard = card;
        quizQuestionNumber = 1;
        quizAsked.add(normalize(card.ilokano));
        return new AiReply(
                "Ilokano Quiz - " + QUIZ_LENGTH + " questions!\n"
                        + "Type \"skip\" to skip a question or \"stop\" to end the quiz.\n\n"
                        + formatQuizQuestion(card, quizQuestionNumber),
                null,
                false
        );
    }

    private String formatQuizQuestion(EntryCard card, int number) {
        return "Question " + number + "/" + QUIZ_LENGTH + "\n"
                + "How do you say \"" + card.english + "\" in Ilokano?"
                + (card.category.isEmpty() ? "" : "\nHint: Category is " + card.category + ".");
    }

    @Nullable
    private EntryCard pickQuizCard() {
        int poolSize = sharedQuizPool.size();
        if (poolSize == 0) {
            return pickRandomCard();
        }
        int start = random.nextInt(poolSize);
        int maxProbe = Math.min(poolSize, 32);
        for (int offset = 0; offset < maxProbe; offset++) {
            EntryCard card = sharedQuizPool.get((start + offset) % poolSize);
            if (!quizAsked.contains(normalize(card.ilokano))) {
                return card;
            }
        }
        return sharedQuizPool.get(start);
    }

    private boolean isQuizStopCommand(String lower) {
        return lower.equals("stop") || lower.equals("quit") || lower.equals("exit")
                || lower.equals("cancel") || lower.equals("end quiz") || lower.equals("stop quiz")
                || lower.equals("end");
    }

    private boolean looksLikeQuestionOrCommand(String lower) {
        if (lower.contains("?")) return true;
        for (String p : QUESTION_PREFIXES) {
            String word = p.trim();
            if (lower.equals(word) || lower.startsWith(word + " ")) return true;
        }
        return false;
    }

    private boolean isCorrectQuizAnswer(String normUser, String rawTarget) {
        if (normUser.isEmpty()) return false;
        String[] variants = rawTarget.split("[/,;]");
        for (String variant : variants) {
            String normTarget = normalize(variant);
            if (normTarget.isEmpty()) continue;
            if (normUser.equals(normTarget)) return true;
            if (scoreSimilarityUnbounded(normUser, normTarget) >= 0.80) return true;
            if (normUser.length() >= 3 && normTarget.contains(normUser)
                    && normUser.length() >= normTarget.length() * 0.7) return true;
        }
        return scoreSimilarityUnbounded(normUser, normalize(rawTarget)) >= 0.80;
    }

    private double scoreSimilarityUnbounded(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 0.0;
        int minLen = Math.min(a.length(), b.length());
        int[] prev = new int[minLen + 1];
        int[] curr = new int[minLen + 1];
        int maxDist = (int) Math.floor(0.25 * maxLen);
        int dist = boundedLevenshtein(a, b, maxDist, prev, curr);
        return dist < 0 ? 0.0 : 1.0 - ((double) dist / maxLen);
    }

    private AiReply handleQuizAnswer(String userAnswer) {
        EntryCard target = pendingQuizCard;
        if (target == null) {
            return buildQuizQuestion();
        }

        String normUser = normalize(userAnswer);
        boolean skipped = normUser.equals("skip") || normUser.equals("idk") || normUser.equals("next")
                || normUser.contains("don t know") || normUser.contains("dont know");
        boolean correct = !skipped && isCorrectQuizAnswer(normUser, target.ilokano);

        StringBuilder sb = new StringBuilder();
        if (correct) {
            quizCorrect++;
            quizStreak++;
            quizBestStreak = Math.max(quizBestStreak, quizStreak);
            sb.append("Correct! \"").append(target.ilokano).append("\" is right.");
            if (quizStreak >= 2) {
                sb.append("\nStreak: ").append(quizStreak).append(" in a row!");
            }
        } else if (skipped) {
            quizStreak = 0;
            sb.append("No worries! \"").append(target.english).append("\" is \"")
                    .append(target.ilokano).append("\" in Ilokano.");
        } else {
            quizStreak = 0;
            sb.append("Not quite. \"").append(target.english).append("\" is \"")
                    .append(target.ilokano).append("\" in Ilokano.");
        }
        sb.append("\nScore: ").append(quizCorrect).append("/").append(quizQuestionNumber);

        if (quizQuestionNumber >= QUIZ_LENGTH) {
            sb.append("\n\n").append(buildQuizSummary(QUIZ_LENGTH, true));
            resetQuizState();
            return new AiReply(sb.toString(), target, false);
        }

        EntryCard next = pickQuizCard();
        if (next == null) {
            sb.append("\n\n").append(buildQuizSummary(quizQuestionNumber, true));
            resetQuizState();
            return new AiReply(sb.toString(), target, false);
        }

        quizQuestionNumber++;
        pendingQuizCard = next;
        quizAsked.add(normalize(next.ilokano));
        sb.append("\n\n").append(formatQuizQuestion(next, quizQuestionNumber));
        return new AiReply(sb.toString(), target, false);
    }

    private String buildQuizSummary(int answered, boolean completed) {
        StringBuilder sb = new StringBuilder();
        sb.append(completed ? "Quiz complete!" : "Quiz ended.");
        sb.append(" You got ").append(quizCorrect).append(" out of ").append(answered).append(".");
        if (quizBestStreak >= 2) {
            sb.append("\nBest streak: ").append(quizBestStreak).append(".");
        }
        if (answered > 0) {
            double ratio = (double) quizCorrect / answered;
            if (ratio >= 1.0) {
                sb.append("\nPerfect score! Naimbag unay!");
            } else if (ratio >= 0.8) {
                sb.append("\nGreat job! You're getting fluent.");
            } else if (ratio >= 0.4) {
                sb.append("\nGood effort! Keep practicing.");
            } else {
                sb.append("\nKeep going! Tap the speaker on cards to hear the words.");
            }
        }
        sb.append("\nTap \"Quiz me!\" to play again.");
        return sb.toString();
    }

    // ---------------------------------------------------------
    // TWO-PASS INDEXED SEARCH & PRUNED LEVENSHTEIN
    // ---------------------------------------------------------

    private List<ScoredCard> findBestMatches(String query, int limit) {
        String normQuery = normalize(query);
        if (normQuery.isEmpty() || sharedIndex.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredCard> fastMatches = new ArrayList<>();
        Set<IndexedEntry> seenFast = null;
        double topFastScore = 0.0;

        // Pass 1: Fast string match across pre-normalized index (no Levenshtein)
        for (int i = 0, size = sharedIndex.size(); i < size; i++) {
            IndexedEntry entry = sharedIndex.get(i);
            double score = Math.max(
                    scoreFieldFast(normQuery, entry.normEn),
                    Math.max(scoreFieldFast(normQuery, entry.normIlo), scoreFieldFast(normQuery, entry.normTl))
            );
            if (score > 0.0) {
                double boosted = score + (entry.isPhrase ? 0.03 : 0.0);
                if (boosted > topFastScore) topFastScore = boosted;
                fastMatches.add(new ScoredCard(entry.card, boosted));
                if (seenFast == null) seenFast = new HashSet<>();
                seenFast.add(entry);
            }
        }

        if (fastMatches.size() >= limit || topFastScore >= 0.85) {
            Collections.sort(fastMatches, (a, b) -> Double.compare(b.score, a.score));
            return fastMatches.size() > limit
                    ? new ArrayList<>(fastMatches.subList(0, limit))
                    : fastMatches;
        }

        // Pass 2: Typo fallback only when Pass 1 did not find enough or strong matches
        int queryLen = normQuery.length();
        int[] prev = new int[queryLen + 1];
        int[] curr = new int[queryLen + 1];

        for (int i = 0, size = sharedIndex.size(); i < size; i++) {
            IndexedEntry entry = sharedIndex.get(i);
            if (seenFast != null && seenFast.contains(entry)) {
                continue;
            }
            double minThreshold = entry.isPhrase ? 0.42 : 0.45;
            double fuzzyScore = Math.max(
                    scoreFieldFuzzy(normQuery, entry.normEn, minThreshold, prev, curr),
                    Math.max(
                            scoreFieldFuzzy(normQuery, entry.normIlo, minThreshold, prev, curr),
                            scoreFieldFuzzy(normQuery, entry.normTl, minThreshold, prev, curr)
                    )
            );
            if (fuzzyScore >= minThreshold) {
                fastMatches.add(new ScoredCard(entry.card, fuzzyScore + (entry.isPhrase ? 0.03 : 0.0)));
            }
        }

        Collections.sort(fastMatches, (a, b) -> Double.compare(b.score, a.score));
        return fastMatches.size() > limit
                ? new ArrayList<>(fastMatches.subList(0, limit))
                : fastMatches;
    }

    private static double scoreFieldFast(String normQuery, String normField) {
        if (normField.isEmpty()) return 0.0;
        if (normField.equals(normQuery)) return 1.0;
        if (normField.startsWith(normQuery)) return 0.90;
        int idx = normField.indexOf(normQuery);
        if (idx >= 0) {
            boolean startBoundary = idx == 0 || normField.charAt(idx - 1) == ' ';
            int endPos = idx + normQuery.length();
            boolean endBoundary = endPos == normField.length() || normField.charAt(endPos) == ' ';
            return (startBoundary && endBoundary) ? 0.84 : 0.80;
        }
        if (normField.length() >= 3 && normQuery.contains(normField)) {
            return 0.72;
        }
        return 0.0;
    }

    private static double scoreFieldFuzzy(String normQuery, String normField, double minScore,
                                          int[] prev, int[] curr) {
        int fLen = normField.length();
        if (fLen == 0) return 0.0;
        int qLen = normQuery.length();
        int maxLen = Math.max(qLen, fLen);
        int maxDist = (int) Math.floor((1.0 - minScore) * maxLen);
        if (Math.abs(qLen - fLen) > maxDist) {
            return 0.0;
        }
        int dist = boundedLevenshtein(normQuery, normField, maxDist, prev, curr);
        if (dist < 0) return 0.0;
        return 1.0 - ((double) dist / maxLen);
    }

    /**
     * Computes edit distance using reusable 1D rows sized to min(len(a), len(b)) + 1.
     * Returns -1 immediately if the edit distance cannot stay within maxDist.
     */
    private static int boundedLevenshtein(String a, String b, int maxDist, int[] prev, int[] curr) {
        String longStr = a.length() >= b.length() ? a : b;
        String shortStr = a.length() >= b.length() ? b : a;
        int lenL = longStr.length();
        int lenS = shortStr.length();

        if (lenL - lenS > maxDist) return -1;
        if (prev.length <= lenS) {
            prev = new int[lenS + 1];
            curr = new int[lenS + 1];
        }

        for (int j = 0; j <= lenS; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenL; i++) {
            curr[0] = i;
            int rowMin = i;
            char ca = longStr.charAt(i - 1);

            for (int j = 1; j <= lenS; j++) {
                int cost = ca == shortStr.charAt(j - 1) ? 0 : 1;
                int val = Math.min(
                        Math.min(prev[j] + 1, curr[j - 1] + 1),
                        prev[j - 1] + cost
                );
                curr[j] = val;
                if (val < rowMin) rowMin = val;
            }

            if (rowMin > maxDist) {
                return -1;
            }

            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        int finalDist = prev[lenS];
        return finalDist <= maxDist ? finalDist : -1;
    }

    @Nullable
    private EntryCard findExactOrBest(String term) {
        List<ScoredCard> list = findBestMatches(term, 1);
        return list.isEmpty() ? null : list.get(0).card;
    }

    @Nullable
    private EntryCard findByTopicKeywords(List<String> keywords) {
        for (int i = 0, size = sharedPhrases.size(); i < size; i++) {
            IndexedEntry entry = sharedPhrases.get(i);
            for (int k = 0, kSize = keywords.size(); k < kSize; k++) {
                if (entry.lowerEn.contains(keywords.get(k))) {
                    return entry.card;
                }
            }
        }
        return pickRandomCard();
    }

    private List<EntryCard> findByCategoryOrTopic(String topic, int limit) {
        String lowerTopic = topic.toLowerCase(Locale.ROOT);
        List<EntryCard> matches = new ArrayList<>(limit);

        for (int i = 0, size = sharedIndex.size(); i < size; i++) {
            IndexedEntry entry = sharedIndex.get(i);
            if (entry.lowerCat.contains(lowerTopic) || entry.lowerEn.contains(lowerTopic)) {
                matches.add(entry.card);
                if (matches.size() >= limit) return matches;
            }
        }
        return matches;
    }

    @Nullable
    private EntryCard pickRandomCard() {
        int size = sharedIndex.size();
        if (size == 0) return null;
        return sharedIndex.get(random.nextInt(size)).card;
    }

    @Nullable
    private String detectCategoryIntent(String lower) {
        if (containsAny(lower, "greeting", "greetings", "hello phrases", "courtesy")) return "Greetings & Courtesy";
        if (containsAny(lower, "travel", "direction", "directions", "commute", "transport")) return "Transportation & Travel";
        if (containsAny(lower, "food", "dining", "eat", "restaurant", "hungry")) return "Food & Drink";
        if (containsAny(lower, "market", "shopping", "buy", "price", "money")) return "Money & Finance";
        if (containsAny(lower, "emergency", "help phrase", "police", "safety", "security")) return "Conflict & Security";
        if (containsAny(lower, "hospital", "sick", "health", "doctor", "medicine")) return "Body & Health";
        if (containsAny(lower, "weather", "rain", "fog", "cold")) return "Weather";
        if (containsAny(lower, "color", "colors", "shape")) return "Colors & Shapes";
        if (containsAny(lower, "number", "numbers", "count")) return "Numbers & Quantity";
        if (containsAny(lower, "family", "relatives", "friends")) return "People & Relationships";
        return null;
    }

    private boolean isSimpleGreeting(String lower) {
        return SIMPLE_GREETINGS.contains(lower);
    }

    private String extractSearchPhrase(String raw) {
        String s = raw.trim()
                .replaceAll("(?i)^(how do i say|how do you say|how to say|what is|what's|whats|what does|translate|meaning of|ilokano for|ilokano of|in ilokano)\\s+", "")
                .replaceAll("(?i)\\s+(in ilokano|to ilokano|in tagalog|mean|means)\\??$", "")
                .replaceAll("[\"'?!.]", "")
                .trim();
        return s.isEmpty() ? raw.trim() : s;
    }

    private static int countWords(String normalized) {
        if (normalized.isEmpty()) return 0;
        int count = 1;
        for (int i = 0, len = normalized.length(); i < len; i++) {
            if (normalized.charAt(i) == ' ') {
                count++;
            }
        }
        return count;
    }

    private static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        boolean lastSpace = true;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
                lastSpace = false;
            } else if (!lastSpace) {
                sb.append(' ');
                lastSpace = true;
            }
        }
        int outLen = sb.length();
        if (outLen > 0 && sb.charAt(outLen - 1) == ' ') {
            sb.setLength(outLen - 1);
        }
        return sb.toString();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return s != null ? s.trim() : "";
    }
}
