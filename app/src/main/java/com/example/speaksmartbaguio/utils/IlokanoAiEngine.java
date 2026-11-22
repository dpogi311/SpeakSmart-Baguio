package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.speaksmartbaguio.BuildConfig;
import com.example.speaksmartbaguio.Phrase;
import com.example.speaksmartbaguio.Word;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.repository.PhraseRepository;

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
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class IlokanoAiEngine {

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

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    /**
     * Set to true only after the server endpoint POST /api/v1/assistant is deployed.
     * While false, all answers come instantly from the offline dictionary and phrasebook.
     */
    private static final boolean ENABLE_CLOUD_AI = false;

    private final Context appContext;
    private final DictionaryRepository dictionaryRepository;
    private final PhraseRepository phraseRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final OkHttpClient httpClient;

    private List<Word> cachedWords = new ArrayList<>();
    private List<Phrase> cachedPhrases = new ArrayList<>();
    private boolean dataLoaded = false;

    @Nullable
    private EntryCard pendingQuizCard = null;

    public IlokanoAiEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.dictionaryRepository = new DictionaryRepository(appContext);
        this.phraseRepository = new PhraseRepository(appContext);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .build();
        preloadData(null);
    }

    public void preloadData(@Nullable Runnable onReady) {
        dictionaryRepository.getAllWords(wordEntities -> {
            cachedWords = EntityMapper.toWordList(wordEntities);
            phraseRepository.getAllPhrases(phraseEntities -> {
                cachedPhrases = EntityMapper.toPhraseList(phraseEntities);
                dataLoaded = true;
                if (onReady != null) {
                    mainHandler.post(onReady);
                }
            });
        });
    }

    public void resetSession() {
        resetQuizState();
    }

    public void ask(String userInput, @NonNull ReplyCallback callback) {
        String trimmed = userInput != null ? userInput.trim() : "";
        if (trimmed.isEmpty()) {
            mainHandler.post(() -> callback.onReply(new AiReply(
                    "Type or speak a question — for example, \"How do you say thank you?\", \"Travel phrases\", or \"Quiz me!\"",
                    null,
                    false
            )));
            return;
        }

        if (!dataLoaded) {
            preloadData(() -> processQuery(trimmed, callback));
        } else {
            processQuery(trimmed, callback);
        }
    }

    private void processQuery(String query, ReplyCallback callback) {
        String lower = query.toLowerCase(Locale.ROOT).trim();

        // 1. Quiz in progress: grade answers, handle "stop", or leave quiz for normal questions
        if (pendingQuizCard != null) {
            if (isQuizStopCommand(lower)) {
                int answered = Math.max(0, quizQuestionNumber - 1);
                AiReply stopReply = new AiReply(buildQuizSummary(answered, false), null, false);
                resetQuizState();
                mainHandler.post(() -> callback.onReply(stopReply));
                return;
            }
            if (!looksLikeQuestionOrCommand(lower)) {
                AiReply quizFeedback = handleQuizAnswer(query);
                mainHandler.post(() -> callback.onReply(quizFeedback));
                return;
            }
            // Clearly a normal question: end the quiz quietly and answer it below.
            if (!containsAny(lower, "quiz", "test me", "practice", "challenge")) {
                resetQuizState();
            }
        }

        // 2. Quiz request
        if (containsAny(lower, "quiz", "test me", "practice", "challenge")) {
            AiReply quizPrompt = buildQuizQuestion();
            mainHandler.post(() -> callback.onReply(quizPrompt));
            return;
        }

        // 3. Gather best local matches for grounding (both offline fallback and cloud context)
        List<ScoredCard> topMatches = findBestMatches(query, 4);
        EntryCard primaryCard = topMatches.isEmpty() ? null : topMatches.get(0).card;

        // 4. Try optional cloud endpoint if online and not a simple local greeting/tip
        if (ENABLE_CLOUD_AI && NetworkUtil.isOnline(appContext) && !isSimpleGreeting(lower)) {
            tryCloudAssistant(query, topMatches, cloudReply -> {
                if (cloudReply != null) {
                    mainHandler.post(() -> callback.onReply(new AiReply(cloudReply, primaryCard, true)));
                } else {
                    AiReply localReply = buildLocalReply(query, lower, topMatches);
                    mainHandler.post(() -> callback.onReply(localReply));
                }
            });
            return;
        }

        // 5. Offline / immediate local smart reply
        AiReply localReply = buildLocalReply(query, lower, topMatches);
        mainHandler.post(() -> callback.onReply(localReply));
    }

    private void tryCloudAssistant(String query, List<ScoredCard> contextMatches, java.util.function.Consumer<String> onResult) {
        new Thread(() -> {
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

                try (Response response = httpClient.newCall(builder.build()).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String respStr = response.body().string();
                        JSONObject json = new JSONObject(respStr);
                        String reply = json.optString("reply", "").trim();
                        if (!reply.isEmpty()) {
                            onResult.accept(reply);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Endpoint optional; silently fall back to local smart assistant.
            }
            onResult.accept(null);
        }).start();
    }

    private AiReply buildLocalReply(String rawQuery, String lower, List<ScoredCard> matches) {
        // Greetings
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

        // Thanks
        if (containsAny(lower, "thank you", "thanks", "agyamanak", "salamat")) {
            EntryCard thankCard = findExactOrBest("thank you");
            return new AiReply(
                    "Awan aniaman! (You're welcome!)\nIn Ilokano, you can say \"Agyamanak\" (Thank you) or \"Agyamanak unay\" (Thank you very much).",
                    thankCard,
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

        // Baguio City cultural & communication tips
        if (containsAny(lower, "baguio", "jeepney", "etiquette", "culture", "tips")) {
            EntryCard tipCard = findByTopicKeywords(Arrays.asList("please", "thank", "excuse", "where", "how much", "stop"));
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

        // Topic / Category browsing
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

        // Translation / "How do you say X" / General lookup
        String cleanedTarget = extractSearchPhrase(rawQuery);
        List<ScoredCard> targetMatches = cleanedTarget.equalsIgnoreCase(rawQuery)
                ? matches
                : findBestMatches(cleanedTarget, 4);

        if (targetMatches.isEmpty() && !matches.isEmpty()) {
            targetMatches = matches;
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
    // QUIZ MODE (5-question rounds with score and streak)
    // ---------------------------------------------------------

    private static final int QUIZ_LENGTH = 5;

    private int quizQuestionNumber = 0;
    private int quizCorrect = 0;
    private int quizStreak = 0;
    private int quizBestStreak = 0;
    private final Set<String> quizAsked = new HashSet<>();

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

    /** Prefers short entries (1-3 words) that have not been asked this round. */
    @Nullable
    private EntryCard pickQuizCard() {
        for (int attempt = 0; attempt < 40; attempt++) {
            EntryCard card = pickRandomCard();
            if (card == null) {
                return null;
            }
            String key = normalize(card.ilokano);
            int wordCount = key.isEmpty() ? 0 : key.split(" ").length;
            if (!quizAsked.contains(key) && wordCount > 0 && wordCount <= 3) {
                return card;
            }
        }
        return pickRandomCard();
    }

    private boolean isQuizStopCommand(String lower) {
        return lower.equals("stop") || lower.equals("quit") || lower.equals("exit")
                || lower.equals("cancel") || lower.equals("end quiz") || lower.equals("stop quiz")
                || lower.equals("end");
    }

    /**
     * True when the message is clearly a normal question or command instead of a quiz answer,
     * so asking "How do you say water?" mid-quiz is not graded as a wrong answer.
     */
    private boolean looksLikeQuestionOrCommand(String lower) {
        if (lower.contains("?")) return true;
        String[] prefixes = {
                "how ", "what ", "what's ", "whats ", "where ", "when ", "why ", "who ",
                "translate ", "meaning of ", "tell me ", "give me ", "show me ", "can you ",
                "teach me ", "quiz", "help", "baguio", "hello", "hi", "hey"
        };
        for (String p : prefixes) {
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
            if (scoreSimilarity(normUser, normTarget) >= 0.80) return true;
            if (normUser.length() >= 3 && normTarget.contains(normUser)
                    && normUser.length() >= normTarget.length() * 0.7) return true;
        }
        return scoreSimilarity(normUser, normalize(rawTarget)) >= 0.80;
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
    // SEARCH & MATCHING HELPERS
    // ---------------------------------------------------------

    private static class ScoredCard {
        final EntryCard card;
        final double score;

        ScoredCard(EntryCard card, double score) {
            this.card = card;
            this.score = score;
        }
    }

    private List<ScoredCard> findBestMatches(String query, int limit) {
        String normQuery = normalize(query);
        if (normQuery.isEmpty()) return Collections.emptyList();

        List<ScoredCard> results = new ArrayList<>();

        for (Phrase p : cachedPhrases) {
            if (p == null) continue;
            double score = Math.max(
                    scoreField(normQuery, p.getEnglishTranslation()),
                    Math.max(scoreField(normQuery, p.getIlokanoWord()), scoreField(normQuery, p.getTagalogTranslation()))
            );
            if (score >= 0.42) {
                String extra = !safe(p.getIlokanoSentence()).isEmpty() ? p.getIlokanoSentence() : p.getEnglishSentence();
                results.add(new ScoredCard(new EntryCard(
                        p.getIlokanoWord(),
                        p.getEnglishTranslation(),
                        p.getTagalogTranslation(),
                        p.getPartOfSpeech(),
                        p.getCategory(),
                        extra,
                        p.getTtsUrl(),
                        true
                ), score + 0.03)); // slight boost for full phrases
            }
        }

        for (Word w : cachedWords) {
            if (w == null) continue;
            double score = Math.max(
                    scoreField(normQuery, w.getEnglishTranslation()),
                    Math.max(scoreField(normQuery, w.getIlokanoWord()), scoreField(normQuery, w.getTagalogTranslation()))
            );
            if (score >= 0.45) {
                results.add(new ScoredCard(new EntryCard(
                        w.getIlokanoWord(),
                        w.getEnglishTranslation(),
                        w.getTagalogTranslation(),
                        w.getPartOfSpeech(),
                        w.getCategory(),
                        w.getDefinition(),
                        w.getTtsUrl(),
                        false
                ), score));
            }
        }

        Collections.sort(results, (a, b) -> Double.compare(b.score, a.score));
        if (results.size() > limit) {
            return new ArrayList<>(results.subList(0, limit));
        }
        return results;
    }

    @Nullable
    private EntryCard findExactOrBest(String term) {
        List<ScoredCard> list = findBestMatches(term, 1);
        return list.isEmpty() ? null : list.get(0).card;
    }

    @Nullable
    private EntryCard findByTopicKeywords(List<String> keywords) {
        for (Phrase p : cachedPhrases) {
            if (p == null) continue;
            String eng = safe(p.getEnglishTranslation()).toLowerCase(Locale.ROOT);
            for (String kw : keywords) {
                if (eng.contains(kw)) {
                    String extra = !safe(p.getIlokanoSentence()).isEmpty() ? p.getIlokanoSentence() : p.getEnglishSentence();
                    return new EntryCard(p.getIlokanoWord(), p.getEnglishTranslation(), p.getTagalogTranslation(),
                            p.getPartOfSpeech(), p.getCategory(), extra, p.getTtsUrl(), true);
                }
            }
        }
        return pickRandomCard();
    }

    private List<EntryCard> findByCategoryOrTopic(String topic, int limit) {
        String lowerTopic = topic.toLowerCase(Locale.ROOT);
        List<EntryCard> matches = new ArrayList<>();

        for (Phrase p : cachedPhrases) {
            if (p == null) continue;
            String cat = safe(p.getCategory()).toLowerCase(Locale.ROOT);
            String eng = safe(p.getEnglishTranslation()).toLowerCase(Locale.ROOT);
            if (cat.contains(lowerTopic) || eng.contains(lowerTopic)) {
                String extra = !safe(p.getIlokanoSentence()).isEmpty() ? p.getIlokanoSentence() : p.getEnglishSentence();
                matches.add(new EntryCard(p.getIlokanoWord(), p.getEnglishTranslation(), p.getTagalogTranslation(),
                        p.getPartOfSpeech(), p.getCategory(), extra, p.getTtsUrl(), true));
                if (matches.size() >= limit) return matches;
            }
        }

        for (Word w : cachedWords) {
            if (w == null) continue;
            String cat = safe(w.getCategory()).toLowerCase(Locale.ROOT);
            String eng = safe(w.getEnglishTranslation()).toLowerCase(Locale.ROOT);
            if (cat.contains(lowerTopic) || eng.contains(lowerTopic)) {
                matches.add(new EntryCard(w.getIlokanoWord(), w.getEnglishTranslation(), w.getTagalogTranslation(),
                        w.getPartOfSpeech(), w.getCategory(), w.getDefinition(), w.getTtsUrl(), false));
                if (matches.size() >= limit) return matches;
            }
        }

        return matches;
    }

    @Nullable
    private EntryCard pickRandomCard() {
        List<EntryCard> pool = new ArrayList<>();
        for (Phrase p : cachedPhrases) {
            if (p != null && !safe(p.getIlokanoWord()).isEmpty() && !safe(p.getEnglishTranslation()).isEmpty()) {
                String extra = !safe(p.getIlokanoSentence()).isEmpty() ? p.getIlokanoSentence() : p.getEnglishSentence();
                pool.add(new EntryCard(p.getIlokanoWord(), p.getEnglishTranslation(), p.getTagalogTranslation(),
                        p.getPartOfSpeech(), p.getCategory(), extra, p.getTtsUrl(), true));
            }
        }
        for (Word w : cachedWords) {
            if (w != null && !safe(w.getIlokanoWord()).isEmpty() && !safe(w.getEnglishTranslation()).isEmpty()) {
                pool.add(new EntryCard(w.getIlokanoWord(), w.getEnglishTranslation(), w.getTagalogTranslation(),
                        w.getPartOfSpeech(), w.getCategory(), w.getDefinition(), w.getTtsUrl(), false));
            }
        }
        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    @Nullable
    private String detectCategoryIntent(String lower) {
        if (containsAny(lower, "greeting", "greetings", "hello phrases")) return "Greeting";
        if (containsAny(lower, "travel", "direction", "directions", "commute", "transport")) return "Travel";
        if (containsAny(lower, "food", "dining", "eat", "restaurant", "hungry")) return "Food";
        if (containsAny(lower, "market", "shopping", "buy", "price")) return "Shopping";
        if (containsAny(lower, "emergency", "help phrase", "hospital", "police")) return "Emergency";
        if (containsAny(lower, "number", "numbers", "count")) return "Number";
        if (containsAny(lower, "family", "relatives")) return "Family";
        return null;
    }

    private boolean isSimpleGreeting(String lower) {
        Set<String> greetings = new HashSet<>(Arrays.asList(
                "hi", "hello", "hey", "kumusta", "naimbag", "good morning", "good afternoon", "good evening"
        ));
        return greetings.contains(lower);
    }

    private boolean isExplicitCommand(String lower) {
        return containsAny(lower, "quiz", "test me", "baguio", "help", "travel", "food", "greetings", "random");
    }

    private String extractSearchPhrase(String raw) {
        String s = raw.trim()
                .replaceAll("(?i)^(how do i say|how do you say|how to say|what is|what's|whats|what does|translate|meaning of|ilokano for|ilokano of|in ilokano)\\s+", "")
                .replaceAll("(?i)\\s+(in ilokano|to ilokano|in tagalog|mean|means)\\??$", "")
                .replaceAll("[\"'?!.]", "")
                .trim();
        return s.isEmpty() ? raw.trim() : s;
    }

    private double scoreField(String normQuery, String fieldValue) {
        String normField = normalize(fieldValue);
        if (normField.isEmpty()) return 0.0;
        if (normField.equals(normQuery)) return 1.0;
        if (normField.startsWith(normQuery)) return 0.90;
        if (normField.contains(normQuery)) return 0.80;
        if (normQuery.contains(normField) && normField.length() >= 3) return 0.72;
        return scoreSimilarity(normQuery, normField);
    }

    private double scoreSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshtein(a, b);
        return 1.0 - ((double) dist / maxLen);
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
