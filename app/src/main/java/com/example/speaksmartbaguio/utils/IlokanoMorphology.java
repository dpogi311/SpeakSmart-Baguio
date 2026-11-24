package com.example.speaksmartbaguio.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based linguistic utilities for Benguet Ilokano morphology,
 * cross-lingual query tokenization, and learner-friendly affix explanations.
 */
public final class IlokanoMorphology {

    private static final String[] PREFIXES = {
            "makipag", "nakipag", "makapag", "nakapag",
            "panag", "panga", "pag", "pang", "pan", "pam",
            "maka", "naka", "maki", "naki",
            "mang", "nang", "man", "nan", "mam", "nam",
            "nag", "ag", "ma", "na", "ka", "um", "in", "pa", "i"
    };

    private static final String[] SUFFIXES = {
            "tayo", "tay", "ak", "ka", "ko", "mo", "na", "mi", "da", "en", "an", "on"
    };

    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();

    static {
        SYNONYMS.put("hi", Arrays.asList("hello", "greeting", "greetings", "naimbag", "kumusta"));
        SYNONYMS.put("hello", Arrays.asList("hi", "greeting", "naimbag", "kumusta"));
        SYNONYMS.put("bye", Arrays.asList("goodbye", "farewell", "kasta"));
        SYNONYMS.put("goodbye", Arrays.asList("bye", "farewell"));
        SYNONYMS.put("thanks", Arrays.asList("thank", "thank you", "agyamanak", "salamat"));
        SYNONYMS.put("thank", Arrays.asList("thanks", "thank you", "agyamanak", "salamat"));
        SYNONYMS.put("yummy", Arrays.asList("delicious", "tasty", "naimas"));
        SYNONYMS.put("tasty", Arrays.asList("delicious", "yummy", "naimas"));
        SYNONYMS.put("delicious", Arrays.asList("naimas", "tasty", "yummy"));
        SYNONYMS.put("jeep", Arrays.asList("jeepney", "ride", "commute", "agsardeng", "para"));
        SYNONYMS.put("jeepney", Arrays.asList("jeep", "ride", "commute", "agsardeng", "stop"));
        SYNONYMS.put("commute", Arrays.asList("jeepney", "bus", "ride", "travel", "fare"));
        SYNONYMS.put("fare", Arrays.asList("bayad", "price", "cost", "pay", "jeepney"));
        SYNONYMS.put("cost", Arrays.asList("price", "much", "mano", "buy", "pay"));
        SYNONYMS.put("price", Arrays.asList("cost", "much", "mano", "buy", "discount"));
        SYNONYMS.put("cheap", Arrays.asList("inexpensive", "nalaka", "discount", "price"));
        SYNONYMS.put("expensive", Arrays.asList("nangina", "costly", "price"));
        SYNONYMS.put("buy", Arrays.asList("purchase", "gumatang", "gatang", "shop", "market"));
        SYNONYMS.put("palengke", Arrays.asList("market", "tiendaan", "buy", "price"));
        SYNONYMS.put("market", Arrays.asList("tiendaan", "palengke", "buy", "mano"));
        SYNONYMS.put("hungry", Arrays.asList("eat", "food", "mangan", "mabisin"));
        SYNONYMS.put("thirsty", Arrays.asList("drink", "water", "danum", "uminom", "mawaw"));
        SYNONYMS.put("eat", Arrays.asList("food", "mangan", "nangan", "meal"));
        SYNONYMS.put("drink", Arrays.asList("water", "beverage", "uminom", "inom", "danum"));
        SYNONYMS.put("sick", Arrays.asList("ill", "pain", "masakit", "doctor", "medicine", "hospital"));
        SYNONYMS.put("hurt", Arrays.asList("pain", "ache", "masakit", "sakit"));
        SYNONYMS.put("cold", Arrays.asList("chilly", "cool", "lammin", "nalamiis"));
        SYNONYMS.put("hot", Arrays.asList("warm", "napudot", "pudot"));
        SYNONYMS.put("pretty", Arrays.asList("beautiful", "lovely", "napintas"));
        SYNONYMS.put("beautiful", Arrays.asList("pretty", "lovely", "napintas"));
        SYNONYMS.put("handsome", Arrays.asList("guapo", "napintas", "good"));
        SYNONYMS.put("elder", Arrays.asList("apo", "manong", "manang", "respect", "old"));
        SYNONYMS.put("older", Arrays.asList("manong", "manang", "elder", "respect"));
        SYNONYMS.put("sir", Arrays.asList("apo", "respect", "polite"));
        SYNONYMS.put("maam", Arrays.asList("apo", "respect", "polite"));
        SYNONYMS.put("restroom", Arrays.asList("bathroom", "toilet", "cr", "banyo"));
        SYNONYMS.put("toilet", Arrays.asList("bathroom", "restroom", "banyo"));
        SYNONYMS.put("cr", Arrays.asList("bathroom", "restroom", "toilet"));
        SYNONYMS.put("money", Arrays.asList("cash", "kwarta", "peso", "pay"));
        SYNONYMS.put("house", Arrays.asList("home", "balay"));
        SYNONYMS.put("home", Arrays.asList("house", "balay"));
        SYNONYMS.put("friend", Arrays.asList("gayyem", "buddy", "pal"));
        SYNONYMS.put("sleep", Arrays.asList("maturog", "turog", "rest"));
        SYNONYMS.put("walk", Arrays.asList("magna", "pagna", "go"));
        SYNONYMS.put("morning", Arrays.asList("bigat", "naimbag"));
        SYNONYMS.put("afternoon", Arrays.asList("malem", "naimbag"));
        SYNONYMS.put("evening", Arrays.asList("night", "rabii", "naimbag"));
        SYNONYMS.put("night", Arrays.asList("evening", "rabii", "naimbag"));
        SYNONYMS.put("sorry", Arrays.asList("apology", "excuse", "pakawan", "dispensar"));
        SYNONYMS.put("excuse", Arrays.asList("sorry", "dispensar", "pakawan"));
        SYNONYMS.put("help", Arrays.asList("assist", "aid", "tulong", "emergency"));
    }

    private IlokanoMorphology() {
    }

    @NonNull
    public static List<String> tokenize(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = text.length(); i < len; i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (sb.length() > 0) {
                if (sb.length() >= 2) {
                    out.add(sb.toString());
                }
                sb.setLength(0);
            }
        }
        if (sb.length() >= 2) {
            out.add(sb.toString());
        }
        return out;
    }

    @NonNull
    public static List<String> tokenizeContent(@Nullable String text, @NonNull Set<String> stopwords) {
        List<String> all = tokenize(text);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> content = new ArrayList<>(all.size());
        for (String tok : all) {
            if (!stopwords.contains(tok)) {
                content.add(tok);
            }
        }
        return content;
    }

    /**
     * Generates plausible root/stem variants for Ilokano, Tagalog, and English tokens
     * (prefixes, -um-/-in- infixes, reduplication, suffixes, and simple English/Tagalog inflections).
     */
    @NonNull
    public static List<String> generateStemCandidates(@Nullable String rawToken) {
        if (rawToken == null) {
            return Collections.emptyList();
        }
        String tok = rawToken.toLowerCase(Locale.ROOT).trim();
        if (tok.length() < 3) {
            return tok.isEmpty() ? Collections.emptyList() : Collections.singletonList(tok);
        }

        LinkedHashSet<String> stems = new LinkedHashSet<>();
        stems.add(tok);

        // Strip common Ilokano/Tagalog prefixes
        for (String pre : PREFIXES) {
            if (tok.startsWith(pre) && tok.length() - pre.length() >= 3) {
                String stripped = tok.substring(pre.length());
                stems.add(stripped);
                addReduplicationStems(stripped, stems);
                if (pre.endsWith("ng")) {
                    // Nasal substitution e.g. mang->k/p/t, nang->k/p/t (mangan -> kan)
                    stems.add("k" + stripped);
                    stems.add("p" + stripped);
                    stems.add("t" + stripped);
                }
            }
        }

        // Handle -um- and -in- infixes after leading consonant (e.g. gumatang -> gatang, sinurat -> surat)
        if (tok.length() >= 5 && isConsonant(tok.charAt(0))) {
            if (tok.startsWith("um", 1)) {
                String noInfix = tok.charAt(0) + tok.substring(3);
                stems.add(noInfix);
                stems.add("ag" + noInfix);
                stems.add("m" + noInfix);
            } else if (tok.startsWith("in", 1)) {
                String noInfix = tok.charAt(0) + tok.substring(3);
                stems.add(noInfix);
            }
        }

        // Handle common n- -> m- past/future alternation in Ilokano/Tagalog (nangan <-> mangan)
        if (tok.startsWith("n") && tok.length() >= 4) {
            stems.add("m" + tok.substring(1));
        } else if (tok.startsWith("m") && tok.length() >= 4) {
            stems.add("n" + tok.substring(1));
        }

        // Strip pronoun enclitics and object/locative suffixes
        List<String> snapshot = new ArrayList<>(stems);
        for (String base : snapshot) {
            for (String suf : SUFFIXES) {
                if (base.endsWith(suf) && base.length() - suf.length() >= 3) {
                    String s = base.substring(0, base.length() - suf.length());
                    stems.add(s);
                    addReduplicationStems(s, stems);
                }
            }
        }

        // Simple English regular inflections for retrieval fallback
        if (tok.endsWith("ing") && tok.length() > 5) {
            stems.add(tok.substring(0, tok.length() - 3));
            stems.add(tok.substring(0, tok.length() - 3) + "e");
        } else if (tok.endsWith("ied") && tok.length() > 4) {
            stems.add(tok.substring(0, tok.length() - 3) + "y");
        } else if (tok.endsWith("ed") && tok.length() > 4) {
            stems.add(tok.substring(0, tok.length() - 2));
            stems.add(tok.substring(0, tok.length() - 1));
        } else if (tok.endsWith("ies") && tok.length() > 4) {
            stems.add(tok.substring(0, tok.length() - 3) + "y");
        } else if (tok.endsWith("s") && !tok.endsWith("ss") && tok.length() > 3) {
            stems.add(tok.substring(0, tok.length() - 1));
        }

        return new ArrayList<>(stems);
    }

    @NonNull
    public static Set<String> expandQueryTerms(@NonNull List<String> tokens, @NonNull Set<String> stopwords) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String t : tokens) {
            if (stopwords.contains(t)) {
                continue;
            }
            expanded.add(t);
            for (String stem : generateStemCandidates(t)) {
                expanded.add(stem);
            }
            List<String> syns = SYNONYMS.get(t);
            if (syns != null) {
                expanded.addAll(syns);
            }
        }
        return expanded;
    }

    /**
     * Produces a concise learner-friendly morphology / Baguio usage tip when an Ilokano expression
     * exhibits notable affixes or politeness markers.
     */
    @Nullable
    public static String explainMorphology(@Nullable String ilokanoText) {
        if (ilokanoText == null || ilokanoText.trim().isEmpty()) {
            return null;
        }
        String lower = ilokanoText.toLowerCase(Locale.ROOT);
        List<String> notes = new ArrayList<>();
        List<String> tokens = tokenize(lower);

        for (String tok : tokens) {
            if (tok.equals("apo")) {
                addUnique(notes, "\"Apo\" shows respect when addressing elders, vendors, or strangers.");
            } else if (tok.equals("unay")) {
                addUnique(notes, "\"Unay\" intensifies a word (meaning \"very\" or \"really\").");
            } else if (tok.equals("mano")) {
                addUnique(notes, "\"Mano\" asks \"how much\" (prices or quantity).");
            } else if (tok.equals("sadino")) {
                addUnique(notes, "\"Sadino\" asks \"where\" for places and directions.");
            } else if (tok.equals("anya") || tok.equals("ania")) {
                addUnique(notes, "\"Anya\" / \"Ania\" means \"what\" when asking questions.");
            } else if (tok.startsWith("nag") && tok.length() >= 6) {
                addUnique(notes, "The \"nag-\" prefix on \"" + tok + "\" marks a completed past action.");
            } else if (tok.startsWith("ag") && tok.length() >= 5 && !tok.equals("agyamanak")) {
                addUnique(notes, "The \"ag-\" prefix on \"" + tok + "\" forms an active verb (doing an action).");
            } else if (tok.startsWith("na") && tok.length() >= 5 && !tok.equals("naimbag")) {
                addUnique(notes, "The \"na-\" prefix on \"" + tok + "\" often describes a quality or adjective.");
            } else if (tok.equals("agyamanak")) {
                addUnique(notes, "\"Ag-yaman-ak\" literally combines \"ag-\" (do) + \"yaman\" (gratitude) + \"-ak\" (I).");
            } else if (tok.endsWith("ak") && tok.length() >= 6) {
                addUnique(notes, "The suffix \"-ak\" on \"" + tok + "\" means \"I\" (the speaker is doing the action).");
            } else if (tok.endsWith("ka") && tok.length() >= 5) {
                addUnique(notes, "The suffix \"-ka\" / pronoun \"ka\" refers to \"you\" (singular).");
            }
            if (notes.size() >= 2) {
                break;
            }
        }

        if (notes.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(notes.get(i));
        }
        return sb.toString();
    }

    private static void addReduplicationStems(String s, Set<String> out) {
        // CVC reduplication (e.g. basbasa -> basa, sursurat -> surat)
        if (s.length() >= 6 && s.substring(0, 3).equals(s.substring(3, 6))) {
            out.add(s.substring(3));
        }
        // CV reduplication (e.g. babasa -> basa)
        if (s.length() >= 4 && s.substring(0, 2).equals(s.substring(2, 4))) {
            out.add(s.substring(2));
        }
    }

    private static boolean isConsonant(char c) {
        char lc = Character.toLowerCase(c);
        return lc >= 'a' && lc <= 'z' && lc != 'a' && lc != 'e' && lc != 'i' && lc != 'o' && lc != 'u';
    }

    private static void addUnique(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }
}
