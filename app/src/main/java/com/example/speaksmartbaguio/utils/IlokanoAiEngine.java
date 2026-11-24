package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.speaksmartbaguio.database.AppDatabase;
import com.example.speaksmartbaguio.database.DatabaseClient;
import com.example.speaksmartbaguio.entity.DictionaryEntity;
import com.example.speaksmartbaguio.entity.PhraseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Hybrid SpeakSmart AI Engine combining:
 * 1. A trained on-device NLU Classifier (11 conversational intents + 30 topics + BM25 IDF weights)
 * 2. Ilokano morphological affix/reduplication stemming & grammar tips
 * 3. Multi-turn session context memory, Baguio scenarios, word-by-word composition, & adaptive quizzes
 * 4. Modular Firebase AI Logic (Gemini) cloud reasoning grounded via local Room RAG retrieval
 */
public class IlokanoAiEngine {

    private static final String TIMING_TAG = "AiTiming";
    private static final String VERSION_PREFS = "app_versions";
    private static final int QUIZ_LENGTH = 5;

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

    private enum MatchSource {
        ENGLISH, ILOKANO, TAGALOG
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
        final Set<String> iloTokens;
        final Set<String> iloStems;
        final Set<String> enTlTokens;
        final Set<String> allSearchStems;

        IndexedEntry(EntryCard card, boolean isPhrase, Set<String> stopwords) {
            this.card = card;
            this.isPhrase = isPhrase;
            this.normIlo = normalize(card.ilokano);
            this.normEn = normalize(card.english);
            this.normTl = normalize(card.tagalog);
            this.lowerCat = card.category.toLowerCase(Locale.ROOT);
            this.lowerEn = card.english.toLowerCase(Locale.ROOT);
            this.wordCountIlo = countWords(this.normIlo);

            this.iloTokens = new HashSet<>(IlokanoMorphology.tokenize(this.normIlo));
            this.iloStems = new HashSet<>(this.iloTokens);
            for (String tok : this.iloTokens) {
                this.iloStems.addAll(IlokanoMorphology.generateStemCandidates(tok));
            }

            LinkedHashSet<String> et = new LinkedHashSet<>();
            et.addAll(IlokanoMorphology.tokenizeContent(this.normEn, stopwords));
            et.addAll(IlokanoMorphology.tokenizeContent(this.normTl, stopwords));
            this.enTlTokens = et;

            LinkedHashSet<String> all = new LinkedHashSet<>(this.iloStems);
            for (String tok : et) {
                all.addAll(IlokanoMorphology.generateStemCandidates(tok));
            }
            all.addAll(IlokanoMorphology.tokenizeContent(this.lowerCat, stopwords));
            this.allSearchStems = all;
        }
    }

    private static final class ScoredEntry {
        final IndexedEntry entry;
        final double score;
        final MatchSource source;

        ScoredEntry(IndexedEntry entry, double score, MatchSource source) {
            this.entry = entry;
            this.score = score;
            this.source = source;
        }
    }

    private static final Set<String> SIMPLE_GREETINGS = new HashSet<>(Arrays.asList(
            "hi", "hello", "hey", "helo", "yo", "kumusta", "kamusta", "kumusta ka",
            "naimbag", "naimbag nga aldaw", "naimbag nga bigat",
            "good morning", "good afternoon", "good evening", "good day",
            "how are you", "how are you doing", "how are you today",
            "hello how are you", "hi how are you", "hey how are you",
            "whats up", "what s up", "nice to meet you", "good to see you",
            "are you there", "anyone there",
            "hello ai", "hi ai", "hey ai", "hello speaksmart", "hi speaksmart"
    ));

    private static final Set<String> CASUAL_THANKS = new HashSet<>(Arrays.asList(
            "thanks", "thank you", "thank u", "ty", "tysm",
            "thanks a lot", "thank you very much", "thank you so much",
            "salamat", "maraming salamat", "agyamanak",
            "okay thanks", "ok thanks", "okay thank you", "ok thank you", "alright thanks"
    ));

    private static final Set<String> CASUAL_FAREWELLS = new HashSet<>(Arrays.asList(
            "bye", "bye bye", "byebye", "goodbye", "good bye",
            "see you", "see ya", "see you later", "take care",
            "goodnight", "good night", "gtg", "gotta go", "later",
            "paalam", "adios", "babye", "ingat"
    ));

    private static final Set<String> CASUAL_ACKS = new HashSet<>(Arrays.asList(
            "ok", "okay", "oki", "okie", "k", "alright", "aight",
            "got it", "i see", "noted", "cool", "nice", "great", "awesome", "amazing",
            "good", "fine", "i am fine", "im fine", "i m fine",
            "i am good", "im good", "i m good", "doing good", "doing well",
            "haha", "hahaha", "hehe", "lol", "lmao", "yay", "wow",
            "oh", "oh okay", "oh ok", "oh nice", "oh cool",
            "sure", "yes", "yeah", "yup", "yep", "no", "nope", "nah",
            "maybe", "nothing", "never mind", "nevermind",
            "no worries", "np", "no problem",
            "you re welcome", "youre welcome", "your welcome",
            "awan aniaman", "walang anuman"
    ));

    private static final List<String> BAGUIO_TIP_KEYWORDS = Arrays.asList(
            "please", "thank", "excuse", "where", "how much", "stop"
    );

    private static final String[] QUESTION_PREFIXES = {
            "how ", "what ", "what's ", "whats ", "where ", "when ", "why ", "who ",
            "translate ", "meaning of ", "tell me ", "give me ", "show me ", "can you ",
            "teach me ", "explain ", "quiz", "help", "baguio", "hello", "hi", "hey"
    };

    // Single background thread serializes index loading, NLU inference, and session state without lock contention.
    private static final ExecutorService AI_EXECUTOR = Executors.newSingleThreadExecutor();
    private static List<IndexedEntry> sharedIndex = new ArrayList<>();
    private static List<IndexedEntry> sharedPhrases = new ArrayList<>();
    private static List<EntryCard> sharedQuizPool = new ArrayList<>();
    private static Map<String, List<IndexedEntry>> sharedByCategory = new HashMap<>();
    private static String sharedVersionKey = "";

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final IlokanoNlpModel nlpModel;

    // Multi-turn session state (accessed only on AI_EXECUTOR)
    private final List<CloudGeminiService.ChatTurn> conversationHistory = new ArrayList<>();
    @Nullable
    private EntryCard lastDiscussedCard = null;
    private final List<EntryCard> lastMatchCards = new ArrayList<>();
    private int lastMatchCursor = 0;
    @Nullable
    private String lastCategory = null;

    // Quiz state (accessed only on AI_EXECUTOR)
    @Nullable
    private volatile EntryCard pendingQuizCard = null;
    private int quizQuestionNumber = 0;
    private int quizCorrect = 0;
    private int quizStreak = 0;
    private int quizBestStreak = 0;
    private boolean quizReverseMode = false;
    @Nullable
    private String quizTopicFilter = null;
    private final Set<String> quizAsked = new HashSet<>();

    public IlokanoAiEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.nlpModel = IlokanoNlpModel.getInstance(this.appContext);
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
        AI_EXECUTOR.execute(() -> {
            resetQuizState();
            conversationHistory.clear();
            lastDiscussedCard = null;
            lastMatchCards.clear();
            lastMatchCursor = 0;
            lastCategory = null;
        });
    }

    /**
     * Restores conversational context when loading a saved conversation from Chat History.
     */
    public void syncHistoryFromMessages(@Nullable List<CloudGeminiService.ChatTurn> turns,
                                        @Nullable EntryCard mostRecentCard) {
        AI_EXECUTOR.execute(() -> {
            resetQuizState();
            conversationHistory.clear();
            if (turns != null) {
                int start = Math.max(0, turns.size() - 10);
                for (int i = start; i < turns.size(); i++) {
                    conversationHistory.add(turns.get(i));
                }
            }
            lastDiscussedCard = mostRecentCard;
            lastMatchCards.clear();
            if (mostRecentCard != null) {
                lastMatchCards.add(mostRecentCard);
                lastCategory = mostRecentCard.category.isEmpty() ? null : mostRecentCard.category;
            } else {
                lastCategory = null;
            }
            lastMatchCursor = 0;
        });
    }

    public void ask(String userInput, @NonNull ReplyCallback callback) {
        final String trimmed = userInput != null ? userInput.trim() : "";
        if (trimmed.isEmpty()) {
            mainHandler.post(() -> callback.onReply(new AiReply(
                    "Type or speak a question — for example, \"How do you say thank you?\", \"Market dialogue\", \"Ilokano grammar\", or \"Quiz me!\"",
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
        Map<String, List<IndexedEntry>> byCat = new HashMap<>();
        Set<String> stopwords = nlpModel.getStopwords();

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
                IndexedEntry entry = new IndexedEntry(card, true, stopwords);
                indexList.add(entry);
                phraseList.add(entry);
                indexByCategory(byCat, entry);
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
                IndexedEntry entry = new IndexedEntry(card, false, stopwords);
                indexList.add(entry);
                indexByCategory(byCat, entry);
                if (entry.wordCountIlo >= 1 && entry.wordCountIlo <= 3
                        && !card.ilokano.isEmpty() && !card.english.isEmpty()) {
                    quizList.add(card);
                }
            }
        }

        sharedIndex = indexList;
        sharedPhrases = phraseList;
        sharedQuizPool = quizList;
        sharedByCategory = byCat;
        sharedVersionKey = versionKey;

        long loadMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - loadStartNs);
        Log.d(TIMING_TAG, "Indexed " + indexList.size() + " entries (" + byCat.size()
                + " categories, " + quizList.size() + " quiz candidates) in " + loadMs + " ms");
    }

    private static void indexByCategory(Map<String, List<IndexedEntry>> byCat, IndexedEntry entry) {
        if (entry.card.category.isEmpty()) {
            return;
        }
        String key = entry.lowerCat;
        List<IndexedEntry> list = byCat.get(key);
        if (list == null) {
            list = new ArrayList<>();
            byCat.put(key, list);
        }
        list.add(entry);
    }

    private void processQuery(String query, ReplyCallback callback, long startNs) {
        String lower = query.toLowerCase(Locale.ROOT).trim();

        // 1. Active quiz grading, hint requests, stop commands, or mid-quiz exit
        if (pendingQuizCard != null) {
            if (isQuizStopCommand(lower)) {
                int answered = Math.max(0, quizQuestionNumber - 1);
                AiReply stopReply = new AiReply(buildQuizSummary(answered, false), null, false);
                resetQuizState();
                recordTurn(query, stopReply);
                deliverReply(query, stopReply, callback, startNs);
                return;
            }
            if (lower.equals("hint") || lower.equals("give me a hint") || lower.equals("clue")) {
                AiReply hintReply = buildQuizHintReply();
                deliverReply(query, hintReply, callback, startNs);
                return;
            }
            if (!looksLikeQuestionOrCommand(lower)) {
                AiReply quizFeedback = handleQuizAnswer(query);
                recordTurn(query, quizFeedback);
                deliverReply(query, quizFeedback, callback, startNs);
                return;
            }
            if (!isExplicitQuizRequest(lower)) {
                resetQuizState();
            }
        }

        // 2. Start quiz (supports topics like "Quiz me on Food" or "Reverse quiz")
        if (isExplicitQuizRequest(lower)) {
            AiReply quizPrompt = buildQuizQuestion(lower);
            recordTurn(query, quizPrompt);
            deliverReply(query, quizPrompt, callback, startNs);
            return;
        }

        // 2.5 Conversational greetings (handles punctuation like "hello?", "hi!", "kumusta?")
        if (isConversationalGreeting(lower)) {
            AiReply greetingReply = buildGreetingReply();
            recordTurn(query, greetingReply);
            deliverReply(query, greetingReply, callback, startNs);
            return;
        }

        // 3. Fast RAG retrieval to ground both Cloud Gemini & Local NLU
        String extracted = extractSearchPhrase(query);
        List<ScoredEntry> ragMatches = findBestMatches(extracted, 7);
        if (ragMatches.isEmpty() && !extracted.equalsIgnoreCase(query)) {
            ragMatches = findBestMatches(query, 7);
        }
        // If user asked a follow-up pronoun question ("give example", "in tagalog"), also pull phrases for lastCard
        if (isFollowUpIntent(lower) && lastDiscussedCard != null && ragMatches.isEmpty()) {
            ragMatches = findBestMatches(lastDiscussedCard.ilokano, 5);
        }

        // 4. Try Hybrid Cloud Generative AI (Firebase AI Logic - Gemini) grounded with Room RAG
        if (CloudGeminiService.isEnabled(appContext)
                && NetworkUtil.isOnline(appContext)) {

            boolean smallTalk = detectSmallTalkType(query) != null;
            List<EntryCard> groundedCards = new ArrayList<>();
            if (!smallTalk) {
                for (ScoredEntry se : ragMatches) {
                    if (se.score >= 0.68) {
                        groundedCards.add(se.entry.card);
                    }
                }
            }

            EntryCard attachCard = smallTalk
                    ? null
                    : pickBestCardForCloudReply(lower, groundedCards, ragMatches);

            String cloudText = CloudGeminiService.generateGroundedReply(
                    appContext,
                    query,
                    conversationHistory,
                    groundedCards,
                    lastDiscussedCard
            );

            if (cloudText != null) {
                AiReply cloudReply = new AiReply(cloudText, attachCard, true);
                updateSessionMemory(attachCard, groundedCards, smallTalk ? null : detectCategoryMention(lower, false));
                recordTurn(query, cloudReply);
                deliverReply(query, cloudReply, callback, startNs);
                return;
            }
        }

        // 5. Upgraded On-Device Trained NLU + Morphological + Multi-Turn Engine
        AiReply localReply = buildSmartOfflineReply(query, lower, extracted, ragMatches);
        recordTurn(query, localReply);
        deliverReply(query, localReply, callback, startNs);
    }

    @Nullable
    private EntryCard pickBestCardForCloudReply(
            String lower,
            List<EntryCard> groundedCards,
            List<ScoredEntry> scored
    ) {
        if (isConversationalGreeting(lower) || detectSmallTalkType(lower) != null) {
            return null;
        }
        if (isFollowUpIntent(lower) && lastDiscussedCard != null && groundedCards.isEmpty()) {
            return lastDiscussedCard;
        }
        if (scored.isEmpty()) {
            return null;
        }
        if (scored.get(0).score >= 0.56) {
            return scored.get(0).entry.card;
        }
        return null;
    }

    private void recordTurn(String userQuery, AiReply reply) {
        conversationHistory.add(new CloudGeminiService.ChatTurn(true, userQuery));
        conversationHistory.add(new CloudGeminiService.ChatTurn(false, reply.message));
        while (conversationHistory.size() > 12) {
            conversationHistory.remove(0);
        }
    }

    private void updateSessionMemory(
            @Nullable EntryCard primaryCard,
            @Nullable List<EntryCard> matchCards,
            @Nullable String category
    ) {
        if (primaryCard != null) {
            lastDiscussedCard = primaryCard;
            if (!primaryCard.category.isEmpty()) {
                lastCategory = primaryCard.category;
            }
        }
        if (category != null && !category.isEmpty()) {
            lastCategory = category;
        }
        if (matchCards != null && !matchCards.isEmpty()) {
            lastMatchCards.clear();
            lastMatchCards.addAll(matchCards);
            lastMatchCursor = 0;
        }
    }

    private void deliverReply(String query, AiReply reply, ReplyCallback callback, long startNs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        Log.d(TIMING_TAG, "Query \"" + query + "\" answered in " + elapsedMs + " ms (cloud=" + reply.fromCloud + ")");
        mainHandler.post(() -> callback.onReply(reply));
    }

    // ---------------------------------------------------------
    // UPGRADED OFFLINE TRAINED NLU & REASONING ENGINE
    // ---------------------------------------------------------

    private AiReply buildSmartOfflineReply(
            String rawQuery,
            String lower,
            String extractedTarget,
            List<ScoredEntry> ragMatches
    ) {
        // Classify intent using our trained Multinomial Naive Bayes model
        String predictedIntent = "";
        double intentConfidence = 0.0;
        List<IlokanoNlpModel.Prediction> preds = nlpModel.classifyIntent(rawQuery);
        if (!preds.isEmpty()) {
            predictedIntent = preds.get(0).label;
            intentConfidence = preds.get(0).probability;
        }

        // A. Greetings (also catches trained intent classifications with low punctuation noise)
        if (isConversationalGreeting(lower)
                || ("GREETING".equals(predictedIntent) && intentConfidence >= 0.55
                && countWords(normalize(lower)) <= 4 && !hasExplicitTranslationCue(lower))) {
            return buildGreetingReply();
        }

        // B. Help / Capabilities
        if (lower.equals("help") || containsAny(lower, "what can you do", "how to use ai", "who are you")) {
            return new AiReply(
                    "Here is how I can help you master Benguet Ilokano:\n\n"
                            + "1. Two-Way Translation & Meaning: Ask \"How do you say [word]?\", \"What does [Ilokano word] mean?\", or \"What is it in Tagalog?\"\n"
                            + "2. Follow-Up Memory: After any word, ask \"Example sentence?\", \"Tagalog?\", \"Break down each word\", or \"Another one\".\n"
                            + "3. Grammar & Culture: Ask about Ilokano prefixes (ag-, nag-, mang-, na-) or Baguio jeepney & market scenarios.\n"
                            + "4. All 30 Topics & Quizzes: Try \"Food phrases\", \"Weather\", or \"Quiz me on Travel!\"",
                    null,
                    false
            );
        }

        // C. Multi-turn Follow-Up Intents using SessionContext
        if (isFollowUpIntent(lower) || ("FOLLOWUP_CONTEXT".equals(predictedIntent) && intentConfidence >= 0.55)) {
            AiReply followUp = handleFollowUpQuery(lower);
            if (followUp != null) {
                return followUp;
            }
        }

        // C2. Conversational Small Talk / Casual Chat / Practice Invitations
        String smallTalkType = detectSmallTalkType(rawQuery);
        if (smallTalkType != null) {
            return buildSmallTalkReply(smallTalkType);
        }

        // D. Explicit Random / Word of the Day (NEVER triggered on "Teach me how to say ...")
        if (isExplicitRandomRequest(lower)) {
            EntryCard randomCard = pickRandomCard();
            if (randomCard != null) {
                updateSessionMemory(randomCard, Collections.singletonList(randomCard), randomCard.category);
                StringBuilder sb = new StringBuilder();
                sb.append("Word / Phrase of the Moment:\n\"")
                        .append(randomCard.ilokano).append("\" — ").append(randomCard.english);
                if (!randomCard.tagalog.isEmpty()) {
                    sb.append(" (Tagalog: ").append(randomCard.tagalog).append(")");
                }
                String morph = IlokanoMorphology.explainMorphology(randomCard.ilokano);
                if (morph != null) {
                    sb.append("\n\n").append(morph);
                }
                return new AiReply(sb.toString(), randomCard, false);
            }
        }

        // E. Grammar & Affix Tutor
        if (isGrammarQuestion(lower) || ("GRAMMAR_EXPLAIN".equals(predictedIntent) && intentConfidence >= 0.50)) {
            return buildGrammarExplanationReply(lower, ragMatches);
        }

        // F. Baguio Real-Life Scenarios (Market, Jeepney, Dining, Directions, Emergency, Politeness)
        String scenarioKey = detectBaguioScenario(lower);
        if (scenarioKey != null) {
            return buildScenarioReply(scenarioKey);
        }

        // G. Thanks lesson shortcut (keeps "How to say thank you?" quick prompt instant & informative)
        if (lower.equals("how to say thank you?") || lower.equals("how to say thank you")) {
            EntryCard thankCard = findExactOrBest("thank you");
            updateSessionMemory(thankCard, thankCard != null ? Collections.singletonList(thankCard) : null, "Greetings & Courtesy");
            return new AiReply(
                    "In Ilokano, say \"Agyamanak\" (Thank you) or \"Agyamanak unay\" (Thank you very much)!\n"
                            + "Polite tip: Add \"Apo\" when speaking to elders, teachers, or vendors (\"Agyamanak, Apo\").\n"
                            + "Reply with \"Awan aniaman\" for \"You're welcome.\"",
                    thankCard,
                    false
            );
        }

        // H. Category / Topic Exploration (supports ALL 30 database categories!)
        boolean strongTopicIntent = isExplicitCategoryPrompt(lower)
                || ("CATEGORY_EXPLORE".equals(predictedIntent) && intentConfidence >= 0.52);
        String matchedCategory = detectCategoryMention(lower, strongTopicIntent);
        if (matchedCategory != null && (strongTopicIntent || ragMatches.isEmpty() || ragMatches.get(0).score < 0.78)) {
            AiReply catReply = buildCategoryReply(matchedCategory);
            if (catReply != null) {
                return catReply;
            }
        }

        // I. Core Translation / Word Meaning / Tagalog Explanation from Grounded RAG Results
        boolean wantsTagalog = containsAny(lower, "tagalog", "filipino") && !containsAny(lower, "to ilokano", "in ilokano");
        boolean wantsExplainIlo = "EXPLAIN_ILOKANO".equals(predictedIntent)
                || containsAny(lower, "what does", "meaning of", "mean", "define", "in english");

        if (!ragMatches.isEmpty() && ragMatches.get(0).score >= 0.52) {
            return buildGroundedEntryReply(rawQuery, lower, extractedTarget, ragMatches, wantsTagalog, wantsExplainIlo);
        }

        // J. Word-by-Word Composition for natural multi-word sentences not saved verbatim
        List<String> contentTokens = IlokanoMorphology.tokenizeContent(extractedTarget, nlpModel.getStopwords());
        if (contentTokens.size() >= 2) {
            AiReply composed = buildWordByWordComposition(extractedTarget, contentTokens, ragMatches);
            if (composed != null) {
                return composed;
            }
        }

        // K. Fallback using Trained 30-Topic Classifier to suggest relevant real entries instead of dead-ending!
        List<IlokanoNlpModel.Prediction> topicPreds = nlpModel.predictTopCategories(rawQuery, 2);
        if (!topicPreds.isEmpty() && topicPreds.get(0).probability >= 0.14) {
            String bestTopic = topicPreds.get(0).label;
            List<EntryCard> topicCards = findByCategory(bestTopic, 3);
            if (!topicCards.isEmpty()) {
                updateSessionMemory(topicCards.get(0), topicCards, bestTopic);
                StringBuilder sb = new StringBuilder();
                sb.append("I didn't find an exact entry for \"").append(extractedTarget)
                        .append("\", so I matched your question to the topic \"").append(bestTopic).append("\":\n");
                for (EntryCard c : topicCards) {
                    sb.append("\n• ").append(c.ilokano).append(" — ").append(c.english);
                }
                sb.append("\n\nTry asking for a shorter keyword or tap \"Ask more\" below!");
                return new AiReply(sb.toString(), topicCards.get(0), false);
            }
        }

        return new AiReply(
                "I couldn't find an exact match for \"" + extractedTarget + "\" in the local database yet.\n\n"
                        + "Tips for better results:\n"
                        + "• Try a key word or short phrase (e.g., \"water\", \"how much\", \"naimbag\")\n"
                        + "• Ask by scenario (e.g., \"Market dialogue\", \"Jeepney\", \"Baguio tips\")\n"
                        + "• Or connect to the internet so Hybrid Gemini AI can help explain custom sentences!",
                null,
                false
        );
    }

    @Nullable
    private AiReply handleFollowUpQuery(String lower) {
        if (lastDiscussedCard == null && lastCategory == null) {
            return null;
        }

        // More / Next result from previous query or category
        if (containsAny(lower, "another", "more", "next", "else", "other way")) {
            if (lastMatchCards.size() > 1) {
                lastMatchCursor = (lastMatchCursor + 1) % lastMatchCards.size();
                EntryCard next = lastMatchCards.get(lastMatchCursor);
                lastDiscussedCard = next;
                StringBuilder sb = new StringBuilder();
                sb.append("Here is another closely related match:\n\"")
                        .append(next.ilokano).append("\" means \"").append(next.english).append("\"");
                if (!next.tagalog.isEmpty()) {
                    sb.append(" (Tagalog: \"").append(next.tagalog).append("\")");
                }
                sb.append(".");
                return new AiReply(sb.toString(), next, false);
            }
            if (lastCategory != null) {
                return buildCategoryReply(lastCategory);
            }
        }

        if (lastDiscussedCard == null) {
            return null;
        }

        // Tagalog follow-up
        if (containsAny(lower, "tagalog", "filipino")) {
            String tl = !lastDiscussedCard.tagalog.isEmpty() ? lastDiscussedCard.tagalog : "Not recorded for this entry";
            return new AiReply(
                    "In Tagalog, \"" + lastDiscussedCard.ilokano + "\" (\"" + lastDiscussedCard.english
                            + "\") is \"" + tl + "\".",
                    lastDiscussedCard,
                    false
            );
        }

        // Word-by-word breakdown follow-up
        if (containsAny(lower, "break down", "each word", "breakdown", "word by word")) {
            List<String> iloWords = IlokanoMorphology.tokenize(lastDiscussedCard.ilokano);
            if (iloWords.size() >= 2) {
                StringBuilder sb = new StringBuilder("Word-by-word breakdown of \"" + lastDiscussedCard.ilokano + "\":\n");
                for (String w : iloWords) {
                    List<ScoredEntry> matches = findBestMatches(w, 1);
                    if (!matches.isEmpty() && matches.get(0).score >= 0.72) {
                        EntryCard mc = matches.get(0).entry.card;
                        sb.append("\n• ").append(w).append(" -> ").append(mc.english);
                    } else {
                        sb.append("\n• ").append(w).append(" -> (connector / particle)");
                    }
                }
                String morph = IlokanoMorphology.explainMorphology(lastDiscussedCard.ilokano);
                if (morph != null) {
                    sb.append("\n\n").append(morph);
                }
                return new AiReply(sb.toString(), lastDiscussedCard, false);
            }
        }

        // Example sentence / usage follow-up
        if (containsAny(lower, "example", "sentence", "use it", "how do i use", "more about", "explain")) {
            List<EntryCard> sentenceCards = findPhrasesContainingIlokano(lastDiscussedCard.ilokano, 3);
            StringBuilder sb = new StringBuilder();
            if (!lastDiscussedCard.extraInfo.isEmpty()) {
                sb.append(lastDiscussedCard.isPhrase ? "Example usage for \"" : "Definition of \"")
                        .append(lastDiscussedCard.ilokano).append("\":\n")
                        .append(lastDiscussedCard.extraInfo);
            } else {
                sb.append("\"").append(lastDiscussedCard.ilokano).append("\" means \"")
                        .append(lastDiscussedCard.english).append("\".");
            }
            if (!sentenceCards.isEmpty()) {
                sb.append("\n\nSample phrases using this word:");
                for (EntryCard sc : sentenceCards) {
                    sb.append("\n• ").append(sc.ilokano).append(" — ").append(sc.english);
                }
            }
            String morph = IlokanoMorphology.explainMorphology(lastDiscussedCard.ilokano);
            if (morph != null) {
                sb.append("\n\nGrammar Note: ").append(morph);
            }
            return new AiReply(sb.toString(), sentenceCards.isEmpty() ? lastDiscussedCard : sentenceCards.get(0), false);
        }

        // Pronunciation audio reminder follow-up
        if (containsAny(lower, "pronounce", "speak", "sound", "hear", "say it")) {
            return new AiReply(
                    "Tap the blue speaker icon on the card below to hear \"" + lastDiscussedCard.ilokano + "\" spoken aloud!",
                    lastDiscussedCard,
                    false
            );
        }

        return null;
    }

    private AiReply buildGroundedEntryReply(
            String rawQuery,
            String lower,
            String extractedTarget,
            List<ScoredEntry> matches,
            boolean wantsTagalog,
            boolean wantsExplainIlo
    ) {
        ScoredEntry best = matches.get(0);
        EntryCard card = best.entry.card;

        List<EntryCard> matchCards = new ArrayList<>(matches.size());
        for (ScoredEntry se : matches) {
            matchCards.add(se.entry.card);
        }
        updateSessionMemory(card, matchCards, card.category);

        StringBuilder sb = new StringBuilder();
        boolean matchedIlokano = best.source == MatchSource.ILOKANO || wantsExplainIlo;

        if (wantsTagalog) {
            sb.append("Tagalog equivalent: \"").append(card.tagalog.isEmpty() ? card.english : card.tagalog)
                    .append("\"\nIlokano: \"").append(card.ilokano)
                    .append("\" | English: \"").append(card.english).append("\".");
        } else if (matchedIlokano && best.score >= 0.72) {
            sb.append("\"").append(card.ilokano).append("\"");
            if (!card.partOfSpeech.isEmpty()) {
                sb.append(" (").append(card.partOfSpeech).append(")");
            }
            sb.append(" means \"").append(card.english).append("\" in English");
            if (!card.tagalog.isEmpty()) {
                sb.append(" and \"").append(card.tagalog).append("\" in Tagalog");
            }
            sb.append(".");
        } else if (best.score >= 0.84) {
            sb.append("In Ilokano, \"").append(card.english).append("\" is \"")
                    .append(card.ilokano).append("\".");
            if (!card.tagalog.isEmpty()) {
                sb.append("\nTagalog: \"").append(card.tagalog).append("\"");
            }
        } else {
            sb.append("Closest match for \"").append(extractedTarget).append("\": \"")
                    .append(card.ilokano).append("\" — ").append(card.english);
            if (!card.tagalog.isEmpty()) {
                sb.append(" (Tagalog: ").append(card.tagalog).append(")");
            }
        }

        if (!card.extraInfo.isEmpty()) {
            sb.append("\n\n").append(card.isPhrase ? "Example: " : "Meaning/Note: ").append(card.extraInfo);
        }

        String morphNote = IlokanoMorphology.explainMorphology(card.ilokano);
        if (morphNote != null) {
            sb.append("\n\nGrammar Tip: ").append(morphNote);
        }

        if (matches.size() > 1) {
            sb.append("\n\nRelated matches:");
            int shown = 0;
            for (int i = 1; i < matches.size() && shown < 3; i++) {
                EntryCard rel = matches.get(i).entry.card;
                if (rel.ilokano.equalsIgnoreCase(card.ilokano)) continue;
                sb.append("\n• ").append(rel.ilokano).append(" (").append(rel.english).append(")");
                shown++;
            }
        }

        sb.append("\n\nYou can also ask: \"Example sentence?\" or \"Break down each word\".");
        return new AiReply(sb.toString(), card, false);
    }

    @Nullable
    private AiReply buildWordByWordComposition(
            String extractedTarget,
            List<String> tokens,
            List<ScoredEntry> relatedMatches
    ) {
        StringBuilder breakdown = new StringBuilder();
        int matchedCount = 0;
        EntryCard primaryCard = relatedMatches.isEmpty() ? null : relatedMatches.get(0).entry.card;

        for (String tok : tokens) {
            List<ScoredEntry> wordMatches = findBestMatches(tok, 1);
            if (!wordMatches.isEmpty() && wordMatches.get(0).score >= 0.72) {
                EntryCard wc = wordMatches.get(0).entry.card;
                breakdown.append("\n• ").append(tok).append(" -> ").append(wc.ilokano);
                if (!wc.english.isEmpty() && !wc.english.equalsIgnoreCase(tok)) {
                    breakdown.append(" (").append(wc.english).append(")");
                }
                if (primaryCard == null) {
                    primaryCard = wc;
                }
                matchedCount++;
            }
        }

        if (matchedCount == 0) {
            return null;
        }

        updateSessionMemory(primaryCard, primaryCard != null ? Collections.singletonList(primaryCard) : null, null);

        StringBuilder sb = new StringBuilder();
        sb.append("I didn't find that exact full sentence, so here is a word-by-word Ilokano guide for \"")
                .append(extractedTarget).append("\":")
                .append(breakdown);

        if (!relatedMatches.isEmpty()) {
            sb.append("\n\nClosest related phrase/entry:");
            for (int i = 0; i < Math.min(2, relatedMatches.size()); i++) {
                EntryCard rc = relatedMatches.get(i).entry.card;
                sb.append("\n• ").append(rc.ilokano).append(" — ").append(rc.english);
            }
        }
        return new AiReply(sb.toString(), primaryCard, false);
    }

    private AiReply buildGrammarExplanationReply(String lower, List<ScoredEntry> ragMatches) {
        EntryCard exampleCard = ragMatches.isEmpty() ? findExactOrBest("thank you") : ragMatches.get(0).entry.card;
        updateSessionMemory(exampleCard, null, "Grammar & Function Words");

        StringBuilder sb = new StringBuilder();
        if (containsAny(lower, "polite", "apo", "respect", "elder", "manong", "manang")) {
            sb.append("Ilokano Politeness & Respectful Address:\n\n")
                    .append("• \"Apo\" — Added to greetings, requests, or thanks when speaking to elders, teachers, drivers, or vendors (e.g., \"Naimbag nga bigat, Apo\").\n")
                    .append("• \"Manong\" / \"Manang\" — Respectful terms for older male / female acquaintances.\n")
                    .append("• \"Mabalin kadi...\" — Polite way to say \"May I...\" or ask permission.");
        } else if (containsAny(lower, "adj", "na-", "nagpintas", "pintas", "describe", "napintas")) {
            sb.append("Ilokano Adjectives (\"na-\" & Intensifiers):\n\n")
                    .append("• Prefix \"na-\" turns a root into a descriptive adjective (e.g., pintas -> napintas \"beautiful\", imas -> naimas \"delicious\", lamiis -> nalamiis \"cold/cool\").\n")
                    .append("• \"Unay\" means \"very/really\" (e.g., \"Naimas unay!\" = \"Very delicious!\").\n")
                    .append("• \"Nag-\" + adjective emphasizes admiration (\"Nagpintas!\" = \"How beautiful!\").");
        } else if (containsAny(lower, "repeat", "redup", "agbasbasa", "agsursurat", "continuous", "tense")) {
            sb.append("Ilokano Verb Tenses & Syllable Repetition (Reduplication):\n\n")
                    .append("• Present / Ongoing action repeats the first consonant-vowel-consonant (CVC) of the root: ag-basa -> agbasbasa (reading now).\n")
                    .append("• Past / Completed uses \"nag-\" or \"nang-\": nagbasa (read), nangan (ate).\n")
                    .append("• Infinitive / Future uses \"ag-\" or \"mang-\": agbasa (will read / to read), mangan (to eat).");
        } else {
            sb.append("Quick Guide to Ilokano Verb Prefixes & Sentence Flow:\n\n")
                    .append("• Verb comes first! Sentences typically follow Verb + Subject + Object (e.g., \"Mangan ak ti innapuy\" = \"I eat rice\").\n")
                    .append("• \"ag-\" / \"nag-\": Actor focus (agtaray = run, nagtaray = ran).\n")
                    .append("• \"mang-\" / \"nang-\": Active transitive verb (mangan = eat, nangan = ate).\n")
                    .append("• \"-en\" / \"-an\": Focuses on the object/location receiving the action (kanen = to be eaten, dalusan = to clean).\n")
                    .append("• Pronouns attach neatly as endings: -ak (I), -ka (you), -tayo (we all).");
        }
        return new AiReply(sb.toString(), exampleCard, false);
    }

    @Nullable
    private String detectBaguioScenario(String lower) {
        if (containsAny(lower, "market", "palengke", "vendor", "bargain", "strawberry", "souvenir", "pasalubong")) {
            return "MARKET";
        }
        if (containsAny(lower, "jeep", "jeepney", "commute", "taxi", "fare", "terminal", "burnham", "session road", "mines view")) {
            return "JEEPNEY";
        }
        if (containsAny(lower, "restaurant", "order food", "dining", "cafe", "eat out")) {
            return "DINING";
        }
        if (containsAny(lower, "baguio", "etiquette", "culture", "cordillera", "tips", "polite tips")) {
            return "ETIQUETTE";
        }
        return null;
    }

    private AiReply buildScenarioReply(String scenario) {
        EntryCard card;
        String text;
        switch (scenario) {
            case "MARKET":
                card = findExactOrBest("how much");
                text = "Baguio Public Market / Vendor Mini-Dialogue:\n\n"
                        + "• Ask the price: \"Mano daytoy, Apo?\" (How much is this, sir/ma'am?)\n"
                        + "• Ask for a discount politely: \"Mabalin ti tawad?\" (Can I ask for a discount?)\n"
                        + "• Thank the vendor: \"Agyamanak unay, Apo!\" (Thank you very much!)\n\n"
                        + "Tap the speaker on the card below to hear the key phrase:";
                break;
            case "JEEPNEY":
                card = findByTopicKeywords(Arrays.asList("stop", "where", "how much", "fare", "jeep"));
                text = "Riding a Baguio Jeepney / Commuting Guide:\n\n"
                        + "• Paying fare: \"Bayad ko, Apo.\" (Here is my fare, please pass it forward.)\n"
                        + "• Stopping at Session Road / Burnham Park: \"Para po!\" or \"Agsardeng ditoy, Apo.\" (Stop here, please!)\n"
                        + "• Asking directions: \"Sadino ti...\" (Where is...)";
                break;
            case "DINING":
                card = findExactOrBest("delicious");
                text = "Ordering & Dining in Baguio:\n\n"
                        + "• Ask for water: \"Mabalin ti danum?\" (May I have water?)\n"
                        + "• Compliment the food: \"Naimas unay!\" (It's very delicious!)\n"
                        + "• Thank your host/server: \"Agyamanak, Apo.\"";
                break;
            case "ETIQUETTE":
            default:
                card = findByTopicKeywords(BAGUIO_TIP_KEYWORDS);
                text = "Baguio Local Etiquette & Communication Tips:\n\n"
                        + "• Show Respect: Add \"Apo\" when speaking to elders, vendors, teachers, or drivers (\"Naimbag nga bigat, Apo\").\n"
                        + "• Jeepneys: Say \"Para po\" or \"Agsardeng ditoy\" when alighting.\n"
                        + "• Markets & Pasalubong: Start politely with \"Mano daytoy?\" (How much is this?) and finish with \"Agyamanak!\".";
                break;
        }
        updateSessionMemory(card, card != null ? Collections.singletonList(card) : null, card != null ? card.category : null);
        return new AiReply(text, card, false);
    }

    @Nullable
    private AiReply buildCategoryReply(String category) {
        List<EntryCard> categoryCards = findByCategory(category, 4);
        if (categoryCards.isEmpty()) {
            return null;
        }
        updateSessionMemory(categoryCards.get(0), categoryCards, category);
        StringBuilder sb = new StringBuilder();
        sb.append("Here are useful \"").append(category).append("\" expressions in Ilokano:\n");
        for (int i = 1; i < categoryCards.size(); i++) {
            EntryCard extra = categoryCards.get(i);
            sb.append("\n• ").append(extra.ilokano).append(" — ").append(extra.english);
            if (!extra.tagalog.isEmpty()) {
                sb.append(" (Tagalog: ").append(extra.tagalog).append(")");
            }
        }
        sb.append("\n\nTap the speaker icon below for audio, or type \"Quiz me on ")
                .append(category.split("&")[0].trim()).append("\" to practice!");
        return new AiReply(sb.toString(), categoryCards.get(0), false);
    }

    // ---------------------------------------------------------
    // QUIZ MODE (Supports Topics, Reverse Mode, Hints, & 5-Question Rounds)
    // ---------------------------------------------------------

    private void resetQuizState() {
        pendingQuizCard = null;
        quizQuestionNumber = 0;
        quizCorrect = 0;
        quizStreak = 0;
        quizBestStreak = 0;
        quizReverseMode = false;
        quizTopicFilter = null;
        quizAsked.clear();
    }

    private AiReply buildQuizQuestion(String lower) {
        resetQuizState();
        quizReverseMode = containsAny(lower, "reverse", "to english", "ilokano to english");
        quizTopicFilter = detectCategoryMention(lower, true);

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

        String modeNote = quizTopicFilter != null ? " (" + quizTopicFilter + ")" : "";
        String dirNote = quizReverseMode ? " [Ilokano -> English]" : "";
        return new AiReply(
                "Ilokano Quiz" + modeNote + dirNote + " — " + QUIZ_LENGTH + " questions!\n"
                        + "Type \"hint\" for a clue, \"skip\" to pass, or \"stop\" to end.\n\n"
                        + formatQuizQuestion(card, quizQuestionNumber),
                null,
                false
        );
    }

    private String formatQuizQuestion(EntryCard card, int number) {
        if (quizReverseMode) {
            return "Question " + number + "/" + QUIZ_LENGTH + "\n"
                    + "What does \"" + card.ilokano + "\" mean in English?"
                    + (card.category.isEmpty() ? "" : "\nCategory: " + card.category);
        }
        return "Question " + number + "/" + QUIZ_LENGTH + "\n"
                + "How do you say \"" + card.english + "\" in Ilokano?"
                + (card.category.isEmpty() ? "" : "\nCategory: " + card.category);
    }

    private AiReply buildQuizHintReply() {
        EntryCard target = pendingQuizCard;
        if (target == null) {
            return buildQuizQuestion("quiz me");
        }
        String answerWord = quizReverseMode ? target.english : target.ilokano;
        String firstChar = answerWord.isEmpty() ? "?" : answerWord.substring(0, 1).toUpperCase(Locale.ROOT);
        int len = normalize(answerWord).length();
        StringBuilder sb = new StringBuilder();
        sb.append("Hint for Question ").append(quizQuestionNumber).append(":\n")
                .append("• Starts with \"").append(firstChar).append("\" (about ").append(len).append(" letters)");
        if (!quizReverseMode && !target.tagalog.isEmpty()) {
            sb.append("\n• Tagalog clue: \"").append(target.tagalog).append("\"");
        }
        if (!target.partOfSpeech.isEmpty()) {
            sb.append("\n• Part of speech: ").append(target.partOfSpeech);
        }
        return new AiReply(sb.toString(), null, false);
    }

    @Nullable
    private EntryCard pickQuizCard() {
        if (quizTopicFilter != null) {
            List<IndexedEntry> catEntries = sharedByCategory.get(quizTopicFilter.toLowerCase(Locale.ROOT));
            if (catEntries != null && !catEntries.isEmpty()) {
                List<EntryCard> candidates = new ArrayList<>();
                for (IndexedEntry ie : catEntries) {
                    if (ie.wordCountIlo >= 1 && ie.wordCountIlo <= 4 && !ie.card.ilokano.isEmpty() && !ie.card.english.isEmpty()) {
                        if (!quizAsked.contains(normalize(ie.card.ilokano))) {
                            candidates.add(ie.card);
                        }
                    }
                }
                if (!candidates.isEmpty()) {
                    return candidates.get(random.nextInt(candidates.size()));
                }
            }
        }

        int poolSize = sharedQuizPool.size();
        if (poolSize == 0) {
            return pickRandomCard();
        }
        int start = random.nextInt(poolSize);
        int maxProbe = Math.min(poolSize, 40);
        for (int offset = 0; offset < maxProbe; offset++) {
            EntryCard card = sharedQuizPool.get((start + offset) % poolSize);
            if (!quizAsked.contains(normalize(card.ilokano))) {
                return card;
            }
        }
        return sharedQuizPool.get(start);
    }

    private AiReply handleQuizAnswer(String userAnswer) {
        EntryCard target = pendingQuizCard;
        if (target == null) {
            return buildQuizQuestion("quiz me");
        }

        String normUser = normalize(userAnswer);
        boolean skipped = normUser.equals("skip") || normUser.equals("idk") || normUser.equals("next")
                || normUser.contains("don t know") || normUser.contains("dont know");
        String expected = quizReverseMode ? target.english : target.ilokano;
        boolean correct = !skipped && isCorrectQuizAnswer(normUser, expected);

        StringBuilder sb = new StringBuilder();
        if (correct) {
            quizCorrect++;
            quizStreak++;
            quizBestStreak = Math.max(quizBestStreak, quizStreak);
            sb.append("Correct! \"").append(expected).append("\" is right.");
            if (quizStreak >= 2) {
                sb.append("\nStreak: ").append(quizStreak).append(" in a row!");
            }
        } else if (skipped) {
            quizStreak = 0;
            sb.append("No worries! Answer: \"").append(target.ilokano).append("\" = \"").append(target.english).append("\".");
        } else {
            quizStreak = 0;
            sb.append("Not quite. Answer: \"").append(target.ilokano).append("\" = \"").append(target.english).append("\".");
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

    private boolean isCorrectQuizAnswer(String normUser, String rawTarget) {
        if (normUser.isEmpty()) return false;
        String[] variants = rawTarget.split("[/,;]");
        for (String variant : variants) {
            String normTarget = normalize(variant);
            if (normTarget.isEmpty()) continue;
            if (normUser.equals(normTarget)) return true;
            if (scoreSimilarityUnbounded(normUser, normTarget) >= 0.78) return true;
            if (normUser.length() >= 3 && normTarget.contains(normUser)
                    && normUser.length() >= normTarget.length() * 0.7) return true;
        }
        return scoreSimilarityUnbounded(normUser, normalize(rawTarget)) >= 0.78;
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
                sb.append("\nKeep going! Tap the speaker on cards to hear pronunciation.");
            }
        }
        sb.append("\nTap \"Quiz me!\" or try \"Quiz me on Food\" anytime.");
        return sb.toString();
    }

    // ---------------------------------------------------------
    // BM25 + MORPHOLOGY + PRUNED FUZZY SEARCH (RAG RETRIEVER)
    // ---------------------------------------------------------

    private List<ScoredEntry> findBestMatches(String query, int limit) {
        String normQuery = normalize(query);
        if (normQuery.isEmpty() || sharedIndex.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> rawTokens = IlokanoMorphology.tokenize(normQuery);
        List<String> contentTokens = IlokanoMorphology.tokenizeContent(normQuery, nlpModel.getStopwords());
        Set<String> expandedTerms = IlokanoMorphology.expandQueryTerms(
                contentTokens.isEmpty() ? rawTokens : contentTokens,
                nlpModel.getStopwords()
        );

        double totalQueryIdf = 0.0;
        for (String t : (contentTokens.isEmpty() ? rawTokens : contentTokens)) {
            totalQueryIdf += nlpModel.getIdf(t);
        }
        if (totalQueryIdf <= 0.0) {
            totalQueryIdf = 1.0;
        }

        List<ScoredEntry> matches = new ArrayList<>();
        Set<IndexedEntry> seen = new HashSet<>();
        double topScore = 0.0;

        for (int i = 0, size = sharedIndex.size(); i < size; i++) {
            IndexedEntry entry = sharedIndex.get(i);
            double bestFieldScore = 0.0;
            MatchSource source = MatchSource.ENGLISH;

            double enScore = scoreFieldFast(normQuery, entry.normEn);
            double iloScore = scoreFieldFast(normQuery, entry.normIlo);
            double tlScore = scoreFieldFast(normQuery, entry.normTl);

            if (iloScore >= enScore && iloScore >= tlScore) {
                bestFieldScore = iloScore;
                source = MatchSource.ILOKANO;
            } else if (tlScore > enScore) {
                bestFieldScore = tlScore;
                source = MatchSource.TAGALOG;
            } else {
                bestFieldScore = enScore;
                source = MatchSource.ENGLISH;
            }

            // Morphological stem + BM25 IDF overlap bonus
            double bm25Score = scoreBm25Morphology(rawTokens, expandedTerms, totalQueryIdf, entry);
            if (bm25Score > bestFieldScore) {
                bestFieldScore = bm25Score;
                source = inferMatchSource(rawTokens, entry);
            }

            if (bestFieldScore > 0.0) {
                boolean preferPhrase = rawTokens.size() > 1;
                double adjusted = bestFieldScore + (entry.isPhrase && preferPhrase ? 0.04 : (entry.isPhrase ? 0.01 : 0.0));
                if (adjusted > topScore) topScore = adjusted;
                matches.add(new ScoredEntry(entry, adjusted, source));
                seen.add(entry);
            }
        }

        // Typo fuzzy fallback only when fast + BM25 didn't find a strong match
        if (matches.size() < limit || topScore < 0.76) {
            int queryLen = normQuery.length();
            int[] prev = new int[queryLen + 1];
            int[] curr = new int[queryLen + 1];

            for (int i = 0, size = sharedIndex.size(); i < size; i++) {
                IndexedEntry entry = sharedIndex.get(i);
                if (seen.contains(entry)) continue;

                double minThresh = entry.isPhrase ? 0.45 : 0.48;
                double sEn = scoreFieldFuzzy(normQuery, entry.normEn, minThresh, prev, curr);
                double sIlo = scoreFieldFuzzy(normQuery, entry.normIlo, minThresh, prev, curr);
                double sTl = scoreFieldFuzzy(normQuery, entry.normTl, minThresh, prev, curr);

                double bestF = Math.max(sEn, Math.max(sIlo, sTl));
                if (bestF >= minThresh) {
                    MatchSource src = sIlo >= sEn && sIlo >= sTl ? MatchSource.ILOKANO
                            : (sTl > sEn ? MatchSource.TAGALOG : MatchSource.ENGLISH);
                    matches.add(new ScoredEntry(entry, bestF, src));
                }
            }
        }

        Collections.sort(matches, (a, b) -> Double.compare(b.score, a.score));
        return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
    }

    private double scoreBm25Morphology(
            List<String> rawTokens,
            Set<String> expandedTerms,
            double totalQueryIdf,
            IndexedEntry entry
    ) {
        if (expandedTerms.isEmpty()) {
            return 0.0;
        }
        double matchedWeight = 0.0;
        boolean iloStemHit = false;
        for (String term : expandedTerms) {
            if (entry.allSearchStems.contains(term)) {
                double w = nlpModel.getIdf(term);
                matchedWeight += w;
                if (entry.iloStems.contains(term)) {
                    iloStemHit = true;
                }
            }
        }
        if (matchedWeight <= 0.0) {
            return 0.0;
        }
        double coverage = Math.min(1.0, matchedWeight / totalQueryIdf);
        // Penalize long entries when query is short single token
        int docTokLen = Math.max(1, entry.allSearchStems.size());
        double lengthNorm = rawTokens.size() == 1 && docTokLen > 6 ? 0.85 : 1.0;
        double score = 0.48 + (0.36 * coverage * lengthNorm);
        if (iloStemHit && rawTokens.size() == 1) {
            score = Math.max(score, 0.80);
        }
        return Math.min(0.88, score);
    }

    private static MatchSource inferMatchSource(List<String> rawTokens, IndexedEntry entry) {
        int iloHits = 0;
        int etHits = 0;
        for (String tok : rawTokens) {
            for (String stem : IlokanoMorphology.generateStemCandidates(tok)) {
                if (entry.iloStems.contains(stem)) iloHits++;
                if (entry.enTlTokens.contains(stem)) etHits++;
            }
        }
        return iloHits > etHits ? MatchSource.ILOKANO : MatchSource.ENGLISH;
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
            return (startBoundary && endBoundary) ? 0.84 : 0.78;
        }
        if (normField.length() >= 3 && normQuery.contains(normField)) {
            int qIdx = normQuery.indexOf(normField);
            boolean startB = qIdx == 0 || normQuery.charAt(qIdx - 1) == ' ';
            int endP = qIdx + normField.length();
            boolean endB = endP == normQuery.length() || normQuery.charAt(endP) == ' ';
            if (startB && endB) {
                return 0.74;
            }
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
        return dist < 0 ? 0.0 : 1.0 - ((double) dist / maxLen);
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
        for (int j = 0; j <= lenS; j++) prev[j] = j;

        for (int i = 1; i <= lenL; i++) {
            curr[0] = i;
            int rowMin = i;
            char ca = longStr.charAt(i - 1);
            for (int j = 1; j <= lenS; j++) {
                int cost = ca == shortStr.charAt(j - 1) ? 0 : 1;
                int val = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                curr[j] = val;
                if (val < rowMin) rowMin = val;
            }
            if (rowMin > maxDist) return -1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        int finalDist = prev[lenS];
        return finalDist <= maxDist ? finalDist : -1;
    }

    // ---------------------------------------------------------
    // HELPERS & INTENT DETECTION
    // ---------------------------------------------------------

    @Nullable
    private EntryCard findExactOrBest(String term) {
        List<ScoredEntry> list = findBestMatches(term, 1);
        return list.isEmpty() ? null : list.get(0).entry.card;
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

    private List<EntryCard> findByCategory(String category, int limit) {
        List<EntryCard> matches = new ArrayList<>(limit);
        List<IndexedEntry> exactList = sharedByCategory.get(category.toLowerCase(Locale.ROOT));
        if (exactList != null && !exactList.isEmpty()) {
            // Prefer phrases first for conversational usefulness
            for (IndexedEntry e : exactList) {
                if (e.isPhrase) {
                    matches.add(e.card);
                    if (matches.size() >= limit) return matches;
                }
            }
            for (IndexedEntry e : exactList) {
                if (!e.isPhrase) {
                    matches.add(e.card);
                    if (matches.size() >= limit) return matches;
                }
            }
            return matches;
        }

        String lowerTopic = category.toLowerCase(Locale.ROOT);
        for (IndexedEntry entry : sharedIndex) {
            if (entry.lowerCat.contains(lowerTopic) || entry.lowerEn.contains(lowerTopic)) {
                matches.add(entry.card);
                if (matches.size() >= limit) return matches;
            }
        }
        return matches;
    }

    private List<EntryCard> findPhrasesContainingIlokano(String ilokanoText, int limit) {
        String normTarget = normalize(ilokanoText);
        if (normTarget.isEmpty()) return Collections.emptyList();
        List<EntryCard> out = new ArrayList<>(limit);
        for (IndexedEntry pe : sharedPhrases) {
            if (!pe.normIlo.equals(normTarget) && pe.normIlo.contains(normTarget)) {
                out.add(pe.card);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    @Nullable
    private EntryCard pickRandomCard() {
        int size = sharedIndex.size();
        if (size == 0) return null;
        return sharedIndex.get(random.nextInt(size)).card;
    }

    @Nullable
    private String detectCategoryMention(String lower, boolean allowModelFallback) {
        // Check all 30 dynamic categories cleanly first
        for (String cat : sharedByCategory.keySet()) {
            if (lower.contains(cat)) {
                return sharedByCategory.get(cat).get(0).card.category;
            }
        }

        if (containsAny(lower, "greeting", "greetings", "courtesy", "hello phrases")) return "Greetings & Courtesy";
        if (containsAny(lower, "travel", "commute", "transport")) return "Transportation & Travel";
        if (containsAny(lower, "food", "dining", "drink", "restaurant")) return "Food & Drink";
        if (containsAny(lower, "money", "finance", "shopping", "bargain")) return "Money & Finance";
        if (containsAny(lower, "emergency", "police", "security", "safety")) return "Conflict & Security";
        if (containsAny(lower, "hospital", "health", "doctor", "medicine", "body")) return "Body & Health";
        if (containsAny(lower, "weather", "rain", "typhoon", "climate")) return "Weather";
        if (containsAny(lower, "color", "colors", "shape", "shapes")) return "Colors & Shapes";
        if (containsAny(lower, "number", "numbers", "counting", "quantity")) return "Numbers & Quantity";
        if (containsAny(lower, "family", "relatives", "relationship", "people")) return "People & Relationships";
        if (containsAny(lower, "animal", "animals", "pets")) return "Animals";
        if (containsAny(lower, "nature", "environment", "mountain", "trees")) return "Nature & Environment";
        if (containsAny(lower, "emotion", "emotions", "feelings")) return "Emotions & Feelings";
        if (containsAny(lower, "education", "school", "study", "knowledge")) return "Education & Knowledge";
        if (containsAny(lower, "time", "clock", "schedule", "frequency")) return "Time & Frequency";
        if (containsAny(lower, "place", "places", "direction", "directions")) return "Places & Directions";
        if (containsAny(lower, "home", "house", "objects", "furniture")) return "Home & Objects";
        if (containsAny(lower, "work", "business", "job", "office")) return "Work & Business";
        if (containsAny(lower, "clothes", "clothing", "wear")) return "Clothing & Accessories";
        if (containsAny(lower, "religion", "church", "prayer", "spiritual")) return "Religion & Spirituality";
        if (containsAny(lower, "sport", "sports", "game", "recreation")) return "Sports & Recreation";
        if (containsAny(lower, "art", "arts", "culture", "music")) return "Arts & Culture";
        if (containsAny(lower, "law", "government", "rights")) return "Law & Government";
        if (containsAny(lower, "technology", "science", "computer", "phone")) return "Technology & Science";
        if (containsAny(lower, "action", "actions", "movement", "verbs")) return "Actions & Movement";
        if (containsAny(lower, "quality", "qualities", "adjectives", "descriptions")) return "Qualities & Descriptions";

        if (allowModelFallback) {
            List<IlokanoNlpModel.Prediction> preds = nlpModel.predictTopCategories(lower, 1);
            if (!preds.isEmpty() && preds.get(0).probability >= 0.16) {
                return preds.get(0).label;
            }
        }
        return null;
    }

    private static boolean isExplicitCategoryPrompt(String lower) {
        return containsAny(lower, "phrases", "vocabulary", "topic", "category", "list", "show me", "browse", "expressions");
    }

    private static boolean isExplicitQuizRequest(String lower) {
        return containsAny(lower, "quiz", "test me", "challenge me", "flashcard", "practice ilokano", "reverse quiz");
    }

    private boolean isQuizStopCommand(String lower) {
        return lower.equals("stop") || lower.equals("quit") || lower.equals("exit")
                || lower.equals("cancel") || lower.equals("end quiz") || lower.equals("stop quiz")
                || lower.equals("end");
    }

    private boolean isFollowUpIntent(String lower) {
        return containsAny(lower,
                "example sentence", "in a sentence", "use it", "more example", "more like",
                "another one", "next one", "what about tagalog", "about in tagalog",
                "break down", "each word", "tell me more", "pronounce it", "other way");
    }

    private boolean isExplicitRandomRequest(String lower) {
        return containsAny(lower, "random", "word of the day", "surprise me", "daily word");
    }

    private static boolean isGrammarQuestion(String lower) {
        return containsAny(lower,
                "grammar", "conjugate", "prefix", "suffix", "infix", "reduplicat",
                "nag-", "ag-", "mang-", "-en", "-an", "verbs work", "past tense",
                "politeness", "why do words repeat");
    }

    private boolean looksLikeQuestionOrCommand(String lower) {
        if (lower.contains("?")) return true;
        for (String p : QUESTION_PREFIXES) {
            String word = p.trim();
            if (lower.equals(word) || lower.startsWith(word + " ")) return true;
        }
        return false;
    }

    private AiReply buildGreetingReply() {
        return new AiReply(
                "Hello! Naimbag nga aldaw! How can I help you today?\n\n"
                        + "You can ask me anything in English, Tagalog, or Ilokano — like \"How do you say thank you?\", "
                        + "\"Market dialogue\", \"Ilokano grammar\", or \"Quiz me!\".",
                null,
                false
        );
    }

    @Nullable
    public static String detectSmallTalkType(@Nullable String rawInput) {
        if (rawInput == null) {
            return null;
        }
        String lower = rawInput.toLowerCase(Locale.ROOT).trim();
        String norm = normalize(lower);
        if (norm.isEmpty() || isConversationalGreeting(lower)) {
            return null;
        }
        if (hasExplicitTranslationCue(lower)
                || isExplicitQuizRequest(lower)
                || isExplicitCategoryPrompt(lower)
                || isGrammarQuestion(lower)) {
            return null;
        }

        int words = countWords(norm);
        if (CASUAL_THANKS.contains(norm)
                || (words <= 7 && containsAny(" " + norm + " ", " thanks ", " thank you ", " salamat "))) {
            return "THANKS";
        }
        if (CASUAL_FAREWELLS.contains(norm)
                || containsAny(norm, "see you tomorrow", "talk to you later", "have a good day", "have a nice day")) {
            return "BYE";
        }
        if (containsAny(norm,
                "can we talk", "lets talk", "let s talk", "lets chat", "let s chat", "can we chat",
                "talk to me", "chat with me", "have a conversation", "make conversation",
                "converse with", "practice conversation", "lets practice", "let s practice",
                "roleplay", "role play", "speak with me", "talk with me", "are you smart",
                "just want to talk")) {
            return "PRACTICE";
        }
        if (CASUAL_ACKS.contains(norm) || allTokensInCasualAck(norm)) {
            return "ACK";
        }
        if (words >= 3 && words <= 18
                && containsAny(" " + norm + " ",
                " my name is ", " i am a ", " im a ", " i m a ",
                " i am from ", " im from ", " i m from ", " i live in ",
                " i am visiting ", " im visiting ", " i m visiting ", " i visited ",
                " call me ", " i love baguio", " i like baguio", " i love ilokano")) {
            return "INTRO";
        }
        return null;
    }

    private static boolean allTokensInCasualAck(String norm) {
        String[] parts = norm.split(" ");
        if (parts.length == 0 || parts.length > 4) {
            return false;
        }
        for (String p : parts) {
            if (!CASUAL_ACKS.contains(p)) {
                return false;
            }
        }
        return true;
    }

    private AiReply buildSmallTalkReply(String type) {
        switch (type) {
            case "THANKS":
                return new AiReply(
                        "You're welcome! (Awan aniaman in Ilokano!) What would you like to talk about or translate next?",
                        null,
                        false
                );
            case "BYE":
                return new AiReply(
                        "See you around — Agannad ka! (Take care in Ilokano!) Come back anytime you want to chat or practice Benguet Ilokano.",
                        null,
                        false
                );
            case "PRACTICE":
                return new AiReply(
                        "Yes! Let's practice together.\n\n"
                                + "Let's start with a Baguio greeting:\n"
                                + "• Me: \"Naimbag nga aldaw! Kumusta ka?\" (Good day! How are you?)\n"
                                + "• Try answering in English, Tagalog, or Ilokano — like \"I'm good, thank you\" or \"Nasayaat met\" — or pick a scenario like \"Market dialogue\" or \"Jeepney\"!",
                        null,
                        false
                );
            case "INTRO":
                return new AiReply(
                        "Nice meeting you! Naimbag nga aldaw!\n"
                                + "Quick Ilokano tip: You can say \"Siak ni [your name]\" (I am [name]) or \"Taga-[place]-ak\" (I am from [place]).\n\n"
                                + "What would you like to practice or ask about Baguio today?",
                        null,
                        false
                );
            case "ACK":
            default:
                return new AiReply(
                        "Got it! Feel free to keep chatting, ask \"How do you say [word]?\", try \"Market dialogue\", or say \"Quiz me!\" anytime.",
                        null,
                        false
                );
        }
    }

    public static boolean isConversationalGreeting(String rawInput) {
        if (rawInput == null) {
            return false;
        }
        String lower = rawInput.toLowerCase(Locale.ROOT).trim();
        String norm = normalize(lower);
        if (norm.isEmpty() || hasExplicitTranslationCue(lower)) {
            return false;
        }
        return SIMPLE_GREETINGS.contains(norm);
    }

    private static boolean hasExplicitTranslationCue(String lower) {
        return containsAny(lower,
                "how do you say", "how do i say", "how to say", "translate",
                "in ilokano", "to ilokano", "in tagalog", "to tagalog",
                "in english", "meaning of", "what does", "what is ");
    }

    private String extractSearchPhrase(String raw) {
        String s = raw.trim()
                .replaceAll("(?i)^(can you |please |could you )+", "")
                .replaceAll("(?i)^(tell me |give me |show me |teach me |explain |translate )", "")
                .replaceAll("(?i)^(how do i say|how do you say|how to say|what is the|what is|what's|whats|what does|meaning of|ilokano for|ilokano of|in ilokano|word for|tagalog of|tagalog for|define)\\s+", "")
                .replaceAll("(?i)\\s+(in ilokano|to ilokano|in tagalog|to tagalog|in english|to english|in filipino|mean|means)\\??$", "")
                .replaceAll("[\"'?!.]", "")
                .trim();
        return s.isEmpty() ? raw.trim() : s;
    }

    private static int countWords(String normalized) {
        if (normalized.isEmpty()) return 0;
        int count = 1;
        for (int i = 0, len = normalized.length(); i < len; i++) {
            if (normalized.charAt(i) == ' ') count++;
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
