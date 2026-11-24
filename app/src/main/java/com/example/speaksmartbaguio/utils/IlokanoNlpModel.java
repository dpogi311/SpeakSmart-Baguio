package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads and evaluates the trained SpeakSmart NLU model (assets/ilokano_ai_model.json):
 * - 11-class Conversational Intent Classifier (Multinomial Naive Bayes with unigrams + bigrams)
 * - 30-class Curated Topic Classifier (Multinomial Naive Bayes with balanced priors)
 * - BM25 Inverse Document Frequency (IDF) statistics & bilingual stopwords across 3,941 entries.
 */
public final class IlokanoNlpModel {

    private static final String TAG = "IlokanoNlpModel";
    private static final String ASSET_NAME = "ilokano_ai_model.json";

    public static final class Prediction {
        public final String label;
        public final double probability;

        public Prediction(String label, double probability) {
            this.label = label;
            this.probability = probability;
        }
    }

    private static volatile IlokanoNlpModel instance;

    private final Set<String> stopwords = new HashSet<>();
    private final Map<String, Double> idfMap = new HashMap<>();
    private final List<String> categories = new ArrayList<>();

    private final Map<String, Double> topicPriors = new HashMap<>();
    private final Map<String, Double> topicDefaultProb = new HashMap<>();
    private final Map<String, Map<String, Double>> topicWeights = new HashMap<>();

    private final List<String> intentLabels = new ArrayList<>();
    private final Map<String, Double> intentPriors = new HashMap<>();
    private final Map<String, Double> intentDefaultProb = new HashMap<>();
    private final Map<String, Map<String, Double>> intentWeights = new HashMap<>();

    private boolean loaded = false;
    private int trainedEntries = 0;

    private IlokanoNlpModel(@NonNull Context context) {
        loadFromAssets(context.getApplicationContext());
    }

    @NonNull
    public static IlokanoNlpModel getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (IlokanoNlpModel.class) {
                if (instance == null) {
                    instance = new IlokanoNlpModel(context);
                }
            }
        }
        return instance;
    }

    private void loadFromAssets(@NonNull Context context) {
        try (InputStream is = context.getAssets().open(ASSET_NAME)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            JSONObject root = new JSONObject(bos.toString(StandardCharsets.UTF_8.name()));

            JSONObject info = root.optJSONObject("modelInfo");
            if (info != null) {
                trainedEntries = info.optInt("trainedOnEntries", 0);
            }

            JSONArray cats = root.optJSONArray("categories");
            if (cats != null) {
                for (int i = 0; i < cats.length(); i++) {
                    categories.add(cats.optString(i));
                }
            }

            JSONArray sw = root.optJSONArray("stopwords");
            if (sw != null) {
                for (int i = 0; i < sw.length(); i++) {
                    stopwords.add(sw.optString(i));
                }
            }

            JSONObject idfObj = root.optJSONObject("idf");
            if (idfObj != null) {
                for (Iterator<String> it = idfObj.keys(); it.hasNext(); ) {
                    String k = it.next();
                    idfMap.put(k, idfObj.optDouble(k, 1.5));
                }
            }

            JSONObject topicObj = root.optJSONObject("topicModel");
            if (topicObj != null) {
                readDoubleMap(topicObj.optJSONObject("priors"), topicPriors);
                readDoubleMap(topicObj.optJSONObject("defaultLogProb"), topicDefaultProb);
                readNestedWeights(topicObj.optJSONObject("weights"), topicWeights);
            }

            JSONObject intentObj = root.optJSONObject("intentModel");
            if (intentObj != null) {
                JSONArray il = intentObj.optJSONArray("labels");
                if (il != null) {
                    for (int i = 0; i < il.length(); i++) {
                        intentLabels.add(il.optString(i));
                    }
                }
                readDoubleMap(intentObj.optJSONObject("priors"), intentPriors);
                readDoubleMap(intentObj.optJSONObject("defaultLogProb"), intentDefaultProb);
                readNestedWeights(intentObj.optJSONObject("weights"), intentWeights);
            }

            loaded = !intentLabels.isEmpty() && !categories.isEmpty();
            Log.d(TAG, "Loaded NLU model: entries=" + trainedEntries
                    + ", topics=" + categories.size()
                    + ", intents=" + intentLabels.size()
                    + ", idfTokens=" + idfMap.size());
        } catch (Exception e) {
            Log.w(TAG, "Failed to load ilokano_ai_model.json; using fallback heuristics.", e);
            loaded = false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    @NonNull
    public Set<String> getStopwords() {
        return stopwords;
    }

    @NonNull
    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public double getIdf(@Nullable String token) {
        if (token == null || token.isEmpty()) {
            return 0.0;
        }
        Double v = idfMap.get(token);
        return v != null ? v : 3.2;
    }

    /**
     * Classifies conversational intent using unigram + bigram log-likelihoods + softmax.
     */
    @NonNull
    public List<Prediction> classifyIntent(@Nullable String query) {
        if (!loaded || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> unigrams = IlokanoMorphology.tokenize(query);
        if (unigrams.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> features = new ArrayList<>(unigrams.size() * 2);
        features.addAll(unigrams);
        for (int i = 0; i < unigrams.size() - 1; i++) {
            features.add(unigrams.get(i) + "_" + unigrams.get(i + 1));
        }

        return scoreAndSoftmax(features, intentLabels, intentPriors, intentDefaultProb, intentWeights);
    }

    /**
     * Predicts top matching database categories (among all 30 curated topics) for a query.
     */
    @NonNull
    public List<Prediction> predictTopCategories(@Nullable String query, int topK) {
        if (!loaded || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> content = IlokanoMorphology.tokenizeContent(query, stopwords);
        if (content.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> features = new ArrayList<>(IlokanoMorphology.expandQueryTerms(content, stopwords));
        List<Prediction> sorted = scoreAndSoftmax(features, categories, topicPriors, topicDefaultProb, topicWeights);
        if (sorted.size() > topK) {
            return new ArrayList<>(sorted.subList(0, topK));
        }
        return sorted;
    }

    @NonNull
    private static List<Prediction> scoreAndSoftmax(
            @NonNull List<String> features,
            @NonNull List<String> labels,
            @NonNull Map<String, Double> priors,
            @NonNull Map<String, Double> defaults,
            @NonNull Map<String, Map<String, Double>> weights
    ) {
        double[] logScores = new double[labels.size()];
        double maxScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            double s = priors.containsKey(label) ? priors.get(label) : 0.0;
            double def = defaults.containsKey(label) ? defaults.get(label) : -8.5;
            Map<String, Double> classWeights = weights.get(label);

            for (String feat : features) {
                Double w = classWeights != null ? classWeights.get(feat) : null;
                s += (w != null ? w : def);
            }
            logScores[i] = s;
            if (s > maxScore) {
                maxScore = s;
            }
        }

        double expSum = 0.0;
        double[] probs = new double[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            probs[i] = Math.exp(Math.max(-40.0, logScores[i] - maxScore));
            expSum += probs[i];
        }

        List<Prediction> results = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            double p = expSum > 0.0 ? probs[i] / expSum : 0.0;
            results.add(new Prediction(labels.get(i), p));
        }
        Collections.sort(results, (a, b) -> Double.compare(b.probability, a.probability));
        return results;
    }

    private static void readDoubleMap(@Nullable JSONObject obj, @NonNull Map<String, Double> target) {
        if (obj == null) return;
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String k = it.next();
            target.put(k, obj.optDouble(k, 0.0));
        }
    }

    private static void readNestedWeights(@Nullable JSONObject obj, @NonNull Map<String, Map<String, Double>> target) {
        if (obj == null) return;
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String cls = it.next();
            JSONObject inner = obj.optJSONObject(cls);
            Map<String, Double> w = new HashMap<>();
            readDoubleMap(inner, w);
            target.put(cls, w);
        }
    }
}
