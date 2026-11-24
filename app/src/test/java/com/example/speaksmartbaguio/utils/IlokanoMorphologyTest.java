package com.example.speaksmartbaguio.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IlokanoMorphologyTest {

    @Test
    public void testPrefixAndReduplicationStemming() {
        List<String> stems = IlokanoMorphology.generateStemCandidates("agbasbasa");
        assertTrue(stems.contains("basa"));

        List<String> pastStems = IlokanoMorphology.generateStemCandidates("nagtaray");
        assertTrue(pastStems.contains("taray"));

        List<String> eatStems = IlokanoMorphology.generateStemCandidates("nangan");
        assertTrue(eatStems.contains("mangan"));
    }

    @Test
    public void testInfixStemming() {
        List<String> buyStems = IlokanoMorphology.generateStemCandidates("gumatang");
        assertTrue(buyStems.contains("gatang"));

        List<String> writeStems = IlokanoMorphology.generateStemCandidates("sinurat");
        assertTrue(writeStems.contains("surat"));
    }

    @Test
    public void testMorphologyLearnerExplanation() {
        String apoTip = IlokanoMorphology.explainMorphology("Agyamanak, Apo");
        assertNotNull(apoTip);
        assertTrue(apoTip.contains("Apo"));

        String pastTip = IlokanoMorphology.explainMorphology("Nagtaray ni gayyem ko");
        assertNotNull(pastTip);
        assertTrue(pastTip.contains("nag-"));
    }

    @Test
    public void testSynonymAndContentExpansion() {
        Set<String> stopwords = new HashSet<>(Arrays.asList("how", "to", "in"));
        List<String> content = IlokanoMorphology.tokenizeContent("how to commute in market", stopwords);
        assertFalse(content.contains("how"));
        Set<String> expanded = IlokanoMorphology.expandQueryTerms(content, stopwords);
        assertTrue(expanded.contains("jeepney"));
        assertTrue(expanded.contains("tiendaan"));
    }

    @Test
    public void testConversationalGreetingDetection() {
        assertTrue(IlokanoAiEngine.isConversationalGreeting("hello?"));
        assertTrue(IlokanoAiEngine.isConversationalGreeting("Hi!"));
        assertTrue(IlokanoAiEngine.isConversationalGreeting("kumusta?"));
        assertTrue(IlokanoAiEngine.isConversationalGreeting("are you there?"));

        assertFalse(IlokanoAiEngine.isConversationalGreeting("how do you say hello?"));
        assertFalse(IlokanoAiEngine.isConversationalGreeting("what does hello mean?"));
        assertFalse(IlokanoAiEngine.isConversationalGreeting("hello in ilokano"));
    }

    @Test
    public void testSmallTalkVsTranslationDetection() {
        assertEquals("THANKS", IlokanoAiEngine.detectSmallTalkType("okay thanks!"));
        assertEquals("THANKS", IlokanoAiEngine.detectSmallTalkType("I'm good, thank you"));
        assertEquals("ACK", IlokanoAiEngine.detectSmallTalkType("haha nice"));
        assertEquals("ACK", IlokanoAiEngine.detectSmallTalkType("ok cool"));
        assertEquals("BYE", IlokanoAiEngine.detectSmallTalkType("bye!"));
        assertEquals("PRACTICE", IlokanoAiEngine.detectSmallTalkType("let's practice conversation"));
        assertEquals("INTRO", IlokanoAiEngine.detectSmallTalkType("I am visiting Baguio tomorrow"));

        // Explicit translation queries MUST NOT be hijacked as small talk
        assertNull(IlokanoAiEngine.detectSmallTalkType("how do you say nice"));
        assertNull(IlokanoAiEngine.detectSmallTalkType("what does cool mean"));
        assertNull(IlokanoAiEngine.detectSmallTalkType("how to say thank you?"));
        assertNull(IlokanoAiEngine.detectSmallTalkType("hello in ilokano"));
        assertNull(IlokanoAiEngine.detectSmallTalkType("I love you"));
    }
}
