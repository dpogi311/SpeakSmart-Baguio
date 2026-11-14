
package com.example.speaksmartbaguio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.speaksmartbaguio.databinding.FragmentTranslatorBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.BaseTransientBottomBar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TranslatorFragment extends Fragment {

    private static final String TAG = "TranslatorFragment";
    private static final int STT_REQUEST_CODE = 100;

    private static final String ARROW_SYMBOL = "⇄";
    private static final int ARROW_COLOR = Color.rgb(13, 163, 150);

    private static final int TEAL_COLOR = Color.rgb(13, 163, 150);
    private static final int SNACKBAR_BACKGROUND = Color.rgb(25, 30, 30);

    private static final String MODE_ENGLISH_TO_ILOKANO =
            "English  ⇄  Ilokano";

    private static final String MODE_ILOKANO_TO_ENGLISH =
            "Ilokano  ⇄  English";

    private static final String MODE_TAGALOG_TO_ILOKANO =
            "Tagalog  ⇄  Ilokano";

    private static final String MODE_ILOKANO_TO_TAGALOG =
            "Ilokano  ⇄  Tagalog";

    private FragmentTranslatorBinding binding;

    private ApiService apiService;
    private DictionaryRepository repository;
    private PhraseRepository phraseRepository;
    private TextToSpeech tts;

    private Snackbar currentSnackbar;

    private final Handler searchHandler =
            new Handler(Looper.getMainLooper());

    private Runnable searchRunnable;

    private String latestInput = "";

    private String translationMode =
            MODE_ENGLISH_TO_ILOKANO;

    // ---------------------------------------------------------
    // FRAGMENT LIFECYCLE
    // ---------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        binding = FragmentTranslatorBinding.inflate(
                inflater,
                container,
                false
        );

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(view, savedInstanceState);

        animateTranslatorEntrance(view);

        apiService = ApiService.getInstance();

        repository = new DictionaryRepository(
                requireContext()
        );

        phraseRepository = new PhraseRepository(
                requireContext()
        );

        setupTextToSpeech();
        setupDropdown();
        setupListeners();
        setupSettingsMenuButton();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                view
        );
    }

    // ---------------------------------------------------------
    // TRANSLATOR ENTRANCE ANIMATION
    // ---------------------------------------------------------

    private void animateTranslatorEntrance(View view) {

        if (view == null) {
            return;
        }

        view.setAlpha(0f);
        view.setTranslationX(25f);

        view.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .setInterpolator(
                        new android.view.animation.DecelerateInterpolator()
                )
                .start();
    }

    // ---------------------------------------------------------
    // SETTINGS MENU
    // ---------------------------------------------------------

    private void setupSettingsMenuButton() {

        binding.buttonSettingsMenu.setOnClickListener(
                v -> SettingsMenuHelper.show(v)
        );
    }

    // ---------------------------------------------------------
    // MODERN MATERIAL SNACKBAR NOTIFICATIONS
    // ---------------------------------------------------------

    private void showMessage(
            String message,
            boolean isError
    ) {

        if (!isAdded()
                || binding == null
                || getView() == null) {
            return;
        }

        // Replace the previous notification.
        if (currentSnackbar != null) {

            currentSnackbar.dismiss();

            currentSnackbar = null;
        }

        Snackbar snackbar = Snackbar.make(
                binding.getRoot(),
                message,
                Snackbar.LENGTH_SHORT
        );

        View snackbarView = snackbar.getView();

        float density = getResources()
                .getDisplayMetrics()
                .density;

        // Rounded dark background.
        GradientDrawable background = new GradientDrawable();

        background.setColor(SNACKBAR_BACKGROUND);

        background.setCornerRadius(
                18f * density
        );

        snackbarView.setBackground(background);

        snackbarView.setElevation(
                12f * density
        );

        // Style the notification message.
        TextView messageView = snackbarView.findViewById(
                com.google.android.material.R.id.snackbar_text
        );

        if (messageView != null) {

            messageView.setTextColor(Color.WHITE);

            messageView.setTextSize(14f);

            messageView.setMaxLines(3);

            messageView.setTypeface(
                    android.graphics.Typeface.create(
                            "sans-serif-medium",
                            android.graphics.Typeface.NORMAL
                    )
            );

            messageView.setGravity(
                    Gravity.CENTER_VERTICAL
            );
        }

        // Teal action color.
        snackbar.setActionTextColor(TEAL_COLOR);

        // Smooth slide animation.
        snackbar.setAnimationMode(
                BaseTransientBottomBar.ANIMATION_MODE_SLIDE
        );

        // Error messages stay slightly longer.
        snackbar.setDuration(
                isError ? 3000 : 2000
        );

        currentSnackbar = snackbar;

        snackbar.addCallback(
                new Snackbar.Callback() {

                    @Override
                    public void onDismissed(
                            Snackbar transientBottomBar,
                            int event
                    ) {

                        super.onDismissed(
                                transientBottomBar,
                                event
                        );

                        if (currentSnackbar == transientBottomBar) {
                            currentSnackbar = null;
                        }
                    }
                }
        );

        snackbar.show();
    }

    // ---------------------------------------------------------
    // ANDROID TEXT-TO-SPEECH
    // ---------------------------------------------------------

    private void setupTextToSpeech() {

        tts = new TextToSpeech(
                requireContext(),
                status -> {

                    if (status != TextToSpeech.SUCCESS) {

                        showMessage(
                                "Speech initialization failed.",
                                true
                        );

                    } else if (tts != null) {

                        tts.setPitch(0.95f);
                        tts.setSpeechRate(0.85f);

                        setTTSLanguage();
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // STYLE TRANSLATION MODE TEXT
    // ---------------------------------------------------------

    private SpannableString getStyledModeText(String mode) {

        SpannableString styledText =
                new SpannableString(mode);

        int arrowStart = mode.indexOf(ARROW_SYMBOL);

        if (arrowStart >= 0) {

            styledText.setSpan(
                    new ForegroundColorSpan(ARROW_COLOR),
                    arrowStart,
                    arrowStart + ARROW_SYMBOL.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return styledText;
    }

    // ---------------------------------------------------------
    // CREATE STYLED DROPDOWN TEXT VIEW
    // ---------------------------------------------------------

    private TextView createStyledModeTextView(
            Context context,
            String mode,
            boolean isDropdownItem
    ) {

        TextView textView = new TextView(context);

        textView.setText(getStyledModeText(mode));

        textView.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.text_primary
                )
        );

        textView.setTextSize(15f);

        textView.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );

        textView.setGravity(Gravity.CENTER_VERTICAL);

        float density = context.getResources()
                .getDisplayMetrics().density;

        int horizontalPadding = (int) (16 * density);
        int verticalPadding = (int) (12 * density);

        textView.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        if (isDropdownItem) {
            textView.setMinHeight((int) (48 * density));
        }

        return textView;
    }

    // ---------------------------------------------------------
    // TRANSLATION MODE DROPDOWN
    // ---------------------------------------------------------

    private void setupDropdown() {

        final String[] modes = {
                MODE_ENGLISH_TO_ILOKANO,
                MODE_ILOKANO_TO_ENGLISH,
                MODE_TAGALOG_TO_ILOKANO,
                MODE_ILOKANO_TO_TAGALOG
        };

        SharedPreferences prefs =
                requireContext().getSharedPreferences(
                        "TranslatorPrefs",
                        Context.MODE_PRIVATE
                );

        translationMode = prefs.getString(
                "translationMode",
                MODE_ENGLISH_TO_ILOKANO
        );

        boolean validMode = false;

        for (String mode : modes) {

            if (mode.equals(translationMode)) {
                validMode = true;
                break;
            }
        }

        if (!validMode) {
            translationMode = MODE_ENGLISH_TO_ILOKANO;
        }

        ArrayAdapter<String> dropdownAdapter =
                new ArrayAdapter<String>(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        new ArrayList<>(Arrays.asList(modes))
                ) {

                    private final Filter showAllModesFilter =
                            new Filter() {

                                @Override
                                protected FilterResults performFiltering(
                                        CharSequence constraint
                                ) {

                                    FilterResults results =
                                            new FilterResults();

                                    ArrayList<String> allModes =
                                            new ArrayList<>(
                                                    Arrays.asList(modes)
                                            );

                                    results.values = allModes;
                                    results.count = allModes.size();

                                    return results;
                                }

                                @Override
                                protected void publishResults(
                                        CharSequence constraint,
                                        FilterResults results
                                ) {

                                    if (results == null
                                            || results.values == null) {
                                        return;
                                    }

                                    @SuppressWarnings("unchecked")
                                    List<String> allModes =
                                            (List<String>) results.values;

                                    clear();
                                    addAll(allModes);
                                    notifyDataSetChanged();
                                }
                            };

                    @NonNull
                    @Override
                    public Filter getFilter() {
                        return showAllModesFilter;
                    }

                    @NonNull
                    @Override
                    public View getView(
                            int position,
                            @Nullable View convertView,
                            @NonNull ViewGroup parent
                    ) {

                        String mode = getItem(position);

                        if (mode == null) {
                            mode = "";
                        }

                        return createStyledModeTextView(
                                requireContext(),
                                mode,
                                false
                        );
                    }

                    @NonNull
                    @Override
                    public View getDropDownView(
                            int position,
                            @Nullable View convertView,
                            @NonNull ViewGroup parent
                    ) {

                        String mode = getItem(position);

                        if (mode == null) {
                            mode = "";
                        }

                        return createStyledModeTextView(
                                requireContext(),
                                mode,
                                true
                        );
                    }
                };

        binding.languageDropdown.setAdapter(dropdownAdapter);

        binding.languageDropdown.setDropDownBackgroundResource(
                R.drawable.dropdown_glass_background
        );

        binding.languageDropdown.setKeyListener(null);
        binding.languageDropdown.setFocusable(false);
        binding.languageDropdown.setInputType(InputType.TYPE_NULL);
        binding.languageDropdown.setThreshold(0);

        binding.languageDropdown.setHint(
                "Select Translation Mode"
        );

        binding.languageDropdown.setText(
                getStyledModeText(translationMode),
                false
        );

        binding.titleText.setText(
                getStyledModeText(translationMode)
        );

        final boolean[] isDropdownOpen = {false};

        View.OnClickListener toggleDropdown = v -> {

            if (binding == null) {
                return;
            }

            if (isDropdownOpen[0]) {

                binding.languageDropdown.dismissDropDown();
                isDropdownOpen[0] = false;

                return;
            }

            dropdownAdapter.getFilter().filter(
                    "",
                    count -> {

                        if (binding == null) {
                            return;
                        }

                        binding.languageDropdown.setAdapter(
                                dropdownAdapter
                        );

                        binding.languageDropdown.setText(
                                getStyledModeText(translationMode),
                                false
                        );

                        binding.languageDropdown.showDropDown();

                        isDropdownOpen[0] = true;
                    }
            );
        };

        binding.languageDropdown.setOnClickListener(
                toggleDropdown
        );

        binding.languageDropdownLayout.setEndIconOnClickListener(
                toggleDropdown
        );

        binding.languageDropdown.setOnItemClickListener(
                (parent, selectedView, position, id) -> {

                    Object selectedItem =
                            parent.getItemAtPosition(position);

                    if (selectedItem == null) {
                        return;
                    }

                    translationMode = selectedItem.toString();

                    prefs.edit()
                            .putString(
                                    "translationMode",
                                    translationMode
                            )
                            .apply();

                    binding.languageDropdown.setText(
                            getStyledModeText(translationMode),
                            false
                    );

                    binding.titleText.setText(
                            getStyledModeText(translationMode)
                    );

                    isDropdownOpen[0] = false;

                    stopTextToSpeech();

                    binding.editTextInput.setText("");
                    binding.originalText.setText("");
                    binding.translatedText.setText("");

                    latestInput = "";

                    searchHandler.removeCallbacksAndMessages(null);

                    setTTSLanguage();
                }
        );

        setTTSLanguage();
    }

    // ---------------------------------------------------------
    // OUTPUT LANGUAGE HELPERS
    // ---------------------------------------------------------

    private boolean isIlokanoOutputMode() {

        return MODE_ENGLISH_TO_ILOKANO.equals(translationMode)
                || MODE_TAGALOG_TO_ILOKANO.equals(translationMode);
    }

    private boolean isEnglishInputMode() {
        return MODE_ENGLISH_TO_ILOKANO.equals(translationMode);
    }

    private boolean isTagalogInputMode() {
        return MODE_TAGALOG_TO_ILOKANO.equals(translationMode);
    }

    private boolean isIlokanoInputMode() {
        return MODE_ILOKANO_TO_ENGLISH.equals(translationMode)
                || MODE_ILOKANO_TO_TAGALOG.equals(translationMode);
    }

    // ---------------------------------------------------------
    // TTS LANGUAGE
    // ---------------------------------------------------------

    private void setTTSLanguage() {

        if (tts == null) {
            return;
        }

        List<Locale> candidates = new ArrayList<>();

        switch (translationMode) {

            case MODE_ILOKANO_TO_ENGLISH:
                candidates.add(new Locale("en", "PH"));
                candidates.add(Locale.US);
                candidates.add(new Locale("fil", "PH"));
                candidates.add(new Locale("tl", "PH"));
                break;

            case MODE_ILOKANO_TO_TAGALOG:
                candidates.add(new Locale("fil", "PH"));
                candidates.add(new Locale("tl", "PH"));
                candidates.add(new Locale("en", "PH"));
                candidates.add(Locale.US);
                break;

            case MODE_ENGLISH_TO_ILOKANO:
            case MODE_TAGALOG_TO_ILOKANO:
            default:
                candidates.add(new Locale("fil", "PH"));
                candidates.add(new Locale("tl", "PH"));
                candidates.add(new Locale("en", "PH"));
                candidates.add(Locale.US);
                break;
        }

        boolean languageSelected = false;

        for (Locale locale : candidates) {

            try {

                int result = tts.setLanguage(locale);

                if (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    Log.i(
                            TAG,
                            "Using Android TTS language: "
                                    + locale.toLanguageTag()
                    );

                    languageSelected = true;
                    break;
                }

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "TTS language unavailable: "
                                + locale.toLanguageTag(),
                        e
                );
            }
        }

        if (!languageSelected) {

            try {

                Locale deviceLocale = Locale.getDefault();

                int result = tts.setLanguage(deviceLocale);

                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {

                    Log.w(
                            TAG,
                            "No preferred TTS language is installed."
                    );
                }

            } catch (Exception e) {

                Log.e(TAG, "Could not set device TTS language.", e);
            }
        }
    }

    // ---------------------------------------------------------
    // LISTENERS
    // ---------------------------------------------------------

    private void setupListeners() {

        binding.editTextInput.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {

                        String input = s.toString().trim();

                        binding.originalText.setText(input);

                        latestInput = input;

                        stopTextToSpeech();

                        searchHandler.removeCallbacksAndMessages(null);

                        if (input.isEmpty()) {

                            binding.translatedText.setText("");

                            return;
                        }

                        searchRunnable =
                                () -> translateTextWordByWord(input);

                        searchHandler.postDelayed(
                                searchRunnable,
                                400
                        );
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );

        binding.micButton.setOnClickListener(
                v -> startSpeechToText()
        );

        binding.ttsButton.setOnClickListener(
                v -> speakTranslation()
        );
    }

    // ---------------------------------------------------------
    // SPEECH-TO-TEXT
    // ---------------------------------------------------------

    private void startSpeechToText() {

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        switch (translationMode) {

            case MODE_ILOKANO_TO_ENGLISH:

                intent.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        "en-US"
                );

                break;

            default:

                intent.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        "fil-PH"
                );

                break;
        }

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak now..."
        );

        try {

            startActivityForResult(
                    intent,
                    STT_REQUEST_CODE
            );

        } catch (Exception e) {

            Log.e(TAG, "Speech recognition unavailable.", e);

            showMessage(
                    "Speech recognition is unavailable.",
                    true
            );
        }
    }

    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == STT_REQUEST_CODE
                && resultCode == Activity.RESULT_OK
                && data != null
                && binding != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                    );

            if (results != null && !results.isEmpty()) {

                binding.editTextInput.setText(results.get(0));
            }
        }
    }

    // ---------------------------------------------------------
    // SPEAKER BUTTON
    // ---------------------------------------------------------

    private void speakTranslation() {

        if (binding == null) {
            return;
        }

        String text = binding.translatedText
                .getText()
                .toString()
                .trim();

        if (text.isEmpty()) {

            showMessage(
                    "Nothing to speak yet.",
                    false
            );

            return;
        }

        speakWithAndroidTts(text);
    }

    // ---------------------------------------------------------
    // ANDROID TEXT-TO-SPEECH PLAYBACK
    // ---------------------------------------------------------

    private void speakWithAndroidTts(String text) {

        if (binding == null || !isAdded()) {
            return;
        }

        if (tts != null) {

            try {

                stopTextToSpeech();

                int result = tts.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "ttsTranslation"
                );

                if (result == TextToSpeech.ERROR) {

                    showMessage(
                            "Unable to play speech. Check your installed TTS voice.",
                            true
                    );
                }

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Android TTS playback failed.",
                        e
                );

                showMessage(
                        "Speech is unavailable on this device.",
                        true
                );
            }

        } else {

            showMessage(
                    "Speech is still initializing. Please try again.",
                    false
            );
        }
    }

    private void stopTextToSpeech() {

        if (tts != null) {

            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------
    // SENTENCE AND MATCHING HELPERS
    // ---------------------------------------------------------

    private String capitalizeSentence(String text) {

        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String trimmed = text.trim();

        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT)
                + trimmed.substring(1);
    }

    private String normalizeForExactMatch(String text) {

        if (text == null) {
            return "";
        }

        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isMultiwordInput(String input) {

        if (input == null) {
            return false;
        }

        String normalized = input.trim();

        if (normalized.isEmpty()) {
            return false;
        }

        return normalized.split("\\s+").length > 1;
    }

    // ---------------------------------------------------------
    // PHRASEBOOK-FIRST TRANSLATION
    // ---------------------------------------------------------

    private void translateTextWordByWord(String input) {

        if (!isAdded() || binding == null) {
            return;
        }

        if (input == null || input.trim().isEmpty()) {
            return;
        }

        if (!isMultiwordInput(input)) {

            translateDictionaryWordByWord(input);

            return;
        }

        if (NetworkUtil.isOnline(requireContext())) {

            searchOnlinePhrasebook(input);

        } else {

            searchOfflinePhrasebook(
                    input,
                    phrase -> {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        if (isSuitablePhrase(phrase)) {

                            displayPhraseTranslation(
                                    input,
                                    phrase
                            );

                        } else {

                            translateOffline(input);
                        }
                    }
            );
        }
    }

    private boolean isCurrentInput(String input) {

        return isAdded()
                && binding != null
                && input != null
                && input.equals(latestInput);
    }

    // ---------------------------------------------------------
    // ONLINE PHRASEBOOK SEARCH
    // ---------------------------------------------------------

    private void searchOnlinePhrasebook(String input) {

        if (!isCurrentInput(input)) {
            return;
        }

        apiService.getPhrasebook(
                1,
                100,
                input,
                new ApiService.ApiCallback<Phrase>() {

                    @Override
                    public void onSuccess(
                            List<Phrase> results,
                            boolean hasMore
                    ) {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        Phrase exactMatch =
                                findExactPhraseMatch(input, results);

                        if (isSuitablePhrase(exactMatch)) {

                            displayPhraseTranslation(
                                    input,
                                    exactMatch
                            );

                        } else {

                            searchOfflinePhrasebook(
                                    input,
                                    phrase -> {

                                        if (!isCurrentInput(input)) {
                                            return;
                                        }

                                        if (isSuitablePhrase(phrase)) {

                                            displayPhraseTranslation(
                                                    input,
                                                    phrase
                                            );

                                        } else {

                                            translateDictionaryWordByWord(
                                                    input
                                            );
                                        }
                                    }
                            );
                        }
                    }

                    @Override
                    public void onError(String error) {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        searchOfflinePhrasebook(
                                input,
                                phrase -> {

                                    if (!isCurrentInput(input)) {
                                        return;
                                    }

                                    if (isSuitablePhrase(phrase)) {

                                        displayPhraseTranslation(
                                                input,
                                                phrase
                                        );

                                    } else {

                                        translateOffline(input);
                                    }
                                }
                        );
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // OFFLINE PHRASEBOOK SEARCH
    // ---------------------------------------------------------

    private interface PhraseLookupCallback {
        void onResult(@Nullable Phrase phrase);
    }

    private void searchOfflinePhrasebook(
            String input,
            PhraseLookupCallback callback
    ) {

        if (phraseRepository == null) {

            callback.onResult(null);

            return;
        }

        phraseRepository.searchPhrases(
                input,
                entities -> {

                    List<Phrase> phrases =
                            EntityMapper.toPhraseList(entities);

                    Phrase exactMatch =
                            findExactPhraseMatch(input, phrases);

                    if (exactMatch != null) {

                        callback.onResult(exactMatch);

                        return;
                    }

                    phraseRepository.getAllPhrases(
                            allEntities -> {

                                List<Phrase> allPhrases =
                                        EntityMapper.toPhraseList(
                                                allEntities
                                        );

                                Phrase fullCollectionMatch =
                                        findExactPhraseMatch(
                                                input,
                                                allPhrases
                                        );

                                callback.onResult(
                                        fullCollectionMatch
                                );
                            }
                    );
                }
        );
    }

    // ---------------------------------------------------------
    // EXACT PHRASEBOOK MATCH
    // ---------------------------------------------------------

    @Nullable
    private Phrase findExactPhraseMatch(
            String input,
            @Nullable List<Phrase> phrases
    ) {

        if (input == null
                || phrases == null
                || phrases.isEmpty()) {

            return null;
        }

        String normalizedInput =
                normalizeForExactMatch(input);

        if (normalizedInput.isEmpty()) {
            return null;
        }

        for (Phrase phrase : phrases) {

            if (phrase == null) {
                continue;
            }

            if (phraseMatchesInput(
                    normalizedInput,
                    phrase
            )) {

                return phrase;
            }
        }

        return null;
    }

    private boolean phraseMatchesInput(
            String normalizedInput,
            Phrase phrase
    ) {

        switch (translationMode) {

            case MODE_ENGLISH_TO_ILOKANO:

                return isExactNormalizedMatch(
                        normalizedInput,
                        phrase.getEnglishSentence()
                ) || isExactNormalizedMatch(
                        normalizedInput,
                        phrase.getEnglishTranslation()
                );

            case MODE_TAGALOG_TO_ILOKANO:

                return isExactNormalizedMatch(
                        normalizedInput,
                        phrase.getTagalogTranslation()
                );

            case MODE_ILOKANO_TO_ENGLISH:
            case MODE_ILOKANO_TO_TAGALOG:

                return isExactNormalizedMatch(
                        normalizedInput,
                        phrase.getIlokanoSentence()
                ) || isExactNormalizedMatch(
                        normalizedInput,
                        phrase.getIlokanoWord()
                );

            default:

                return false;
        }
    }

    private boolean isExactNormalizedMatch(
            String normalizedInput,
            String candidate
    ) {

        if (candidate == null || candidate.trim().isEmpty()) {
            return false;
        }

        return normalizedInput.equals(
                normalizeForExactMatch(candidate)
        );
    }

    // ---------------------------------------------------------
    // PHRASE SUITABILITY
    // ---------------------------------------------------------

    private boolean isSuitablePhrase(@Nullable Phrase phrase) {

        if (phrase == null) {
            return false;
        }

        String output = getPhraseOutput(phrase);

        return output != null && !output.trim().isEmpty();
    }

    // ---------------------------------------------------------
    // GET PHRASE TRANSLATION
    // ---------------------------------------------------------

    @Nullable
    private String getPhraseOutput(Phrase phrase) {

        if (phrase == null) {
            return null;
        }

        String output;

        switch (translationMode) {

            case MODE_ENGLISH_TO_ILOKANO:
            case MODE_TAGALOG_TO_ILOKANO:

                output = phrase.getIlokanoSentence();

                if (output == null || output.trim().isEmpty()) {
                    output = phrase.getIlokanoWord();
                }

                break;

            case MODE_ILOKANO_TO_ENGLISH:

                output = phrase.getEnglishSentence();

                if (output == null || output.trim().isEmpty()) {
                    output = phrase.getEnglishTranslation();
                }

                break;

            case MODE_ILOKANO_TO_TAGALOG:

                output = phrase.getTagalogTranslation();

                break;

            default:

                output = null;

                break;
        }

        if (output == null || output.trim().isEmpty()) {
            return null;
        }

        return output.trim();
    }

    // ---------------------------------------------------------
    // DISPLAY A MATCHED PHRASE
    // ---------------------------------------------------------

    private void displayPhraseTranslation(
            String input,
            Phrase phrase
    ) {

        if (!isCurrentInput(input)) {
            return;
        }

        String output = getPhraseOutput(phrase);

        if (output == null || output.trim().isEmpty()) {

            continueToDictionary(input);

            return;
        }

        showTranslation(capitalizeSentence(output));
    }

    private void continueToDictionary(String input) {

        if (!isCurrentInput(input)) {
            return;
        }

        if (NetworkUtil.isOnline(requireContext())) {

            translateDictionaryWordByWord(input);

        } else {

            translateOffline(input);
        }
    }

    // ---------------------------------------------------------
    // ONLINE DICTIONARY WORD-BY-WORD TRANSLATION
    // ---------------------------------------------------------

    private void translateDictionaryWordByWord(String input) {

        if (!isAdded() || binding == null) {
            return;
        }

        if (!NetworkUtil.isOnline(requireContext())) {

            translateOffline(input);

            return;
        }

        String trimmedInput = input.trim();

        if (trimmedInput.isEmpty()) {
            return;
        }

        String[] words = trimmedInput.split("\\s+");

        final int totalWords = words.length;

        final String[] translatedParts =
                new String[totalWords];

        final int[] completed = {0};

        final boolean[] finalized = {false};

        for (int index = 0; index < totalWords; index++) {

            final int wordIndex = index;

            final String originalWord = words[index];

            String cleanedWord = originalWord
                    .replaceAll("[^\\p{L}\\p{N}]", "")
                    .toLowerCase(Locale.ROOT);

            if (cleanedWord.isEmpty()) {

                translatedParts[wordIndex] = originalWord;

                completeOnlineWord(
                        input,
                        translatedParts,
                        completed,
                        totalWords,
                        finalized
                );

                continue;
            }

            String fieldSearch;

            switch (translationMode) {

                case MODE_ENGLISH_TO_ILOKANO:
                    fieldSearch = "englishTranslation";
                    break;

                case MODE_ILOKANO_TO_ENGLISH:
                    fieldSearch = "ilokanoWord";
                    break;

                case MODE_TAGALOG_TO_ILOKANO:
                    fieldSearch = "tagalogTranslation";
                    break;

                case MODE_ILOKANO_TO_TAGALOG:
                    fieldSearch = "ilokanoWord";
                    break;

                default:
                    fieldSearch = "englishTranslation";
                    break;
            }

            final String originalForCallback = originalWord;

            apiService.searchDictionaryWord(
                    fieldSearch,
                    cleanedWord,
                    new ApiService.ApiCallback<Word>() {

                        @Override
                        public void onSuccess(
                                List<Word> results,
                                boolean hasMore
                        ) {

                            if (!isCurrentInput(input)) {
                                return;
                            }

                            String translated =
                                    originalForCallback;

                            if (results != null && !results.isEmpty()) {

                                Word item = results.get(0);

                                if (item != null) {

                                    switch (translationMode) {

                                        case MODE_ENGLISH_TO_ILOKANO:

                                            if (item.getIlokanoWord() != null
                                                    && !item.getIlokanoWord()
                                                    .trim().isEmpty()) {

                                                translated =
                                                        item.getIlokanoWord();
                                            }

                                            break;

                                        case MODE_ILOKANO_TO_ENGLISH:

                                            if (item.getEnglishTranslation() != null
                                                    && !item.getEnglishTranslation()
                                                    .trim().isEmpty()) {

                                                translated =
                                                        item.getEnglishTranslation();
                                            }

                                            break;

                                        case MODE_TAGALOG_TO_ILOKANO:

                                            if (item.getIlokanoWord() != null
                                                    && !item.getIlokanoWord()
                                                    .trim().isEmpty()) {

                                                translated =
                                                        item.getIlokanoWord();
                                            }

                                            break;

                                        case MODE_ILOKANO_TO_TAGALOG:

                                            if (item.getTagalogTranslation() != null
                                                    && !item.getTagalogTranslation()
                                                    .trim().isEmpty()) {

                                                translated =
                                                        item.getTagalogTranslation();
                                            }

                                            break;
                                    }
                                }
                            }

                            translatedParts[wordIndex] = translated;

                            completeOnlineWord(
                                    input,
                                    translatedParts,
                                    completed,
                                    totalWords,
                                    finalized
                            );
                        }

                        @Override
                        public void onError(String error) {

                            if (!isCurrentInput(input)) {
                                return;
                            }

                            translatedParts[wordIndex] =
                                    originalForCallback;

                            completeOnlineWord(
                                    input,
                                    translatedParts,
                                    completed,
                                    totalWords,
                                    finalized
                            );
                        }
                    }
            );
        }
    }

    // ---------------------------------------------------------
    // COMPLETE ONLINE WORD LOOKUP
    // ---------------------------------------------------------

    private void completeOnlineWord(
            String input,
            String[] translatedParts,
            int[] completed,
            int totalWords,
            boolean[] finalized
    ) {

        if (!isCurrentInput(input)) {
            return;
        }

        completed[0]++;

        if (completed[0] < totalWords || finalized[0]) {
            return;
        }

        finalized[0] = true;

        StringBuilder translatedSentence =
                new StringBuilder();

        for (int i = 0; i < totalWords; i++) {

            String translatedPart = translatedParts[i];

            if (translatedPart != null) {

                translatedSentence
                        .append(translatedPart)
                        .append(" ");
            }
        }

        showTranslation(
                capitalizeSentence(
                        translatedSentence.toString().trim()
                )
        );
    }

    // ---------------------------------------------------------
    // OFFLINE DICTIONARY TRANSLATION
    // ---------------------------------------------------------

    private void translateOffline(String input) {

        if (!isAdded() || binding == null) {
            return;
        }

        String trimmedInput = input.trim();

        if (trimmedInput.isEmpty()) {
            return;
        }

        String[] words = trimmedInput.split("\\s+");

        repository.getAllWords(entities -> {

            if (!isCurrentInput(input)) {
                return;
            }

            List<Word> dictionary =
                    EntityMapper.toWordList(entities);

            String[] translatedParts =
                    new String[words.length];

            for (int i = 0; i < words.length; i++) {

                String originalWord = words[i];

                String clean = originalWord
                        .replaceAll("[^\\p{L}\\p{N}]", "")
                        .toLowerCase(Locale.ROOT);

                String output = originalWord;

                if (!clean.isEmpty()) {

                    for (Word item : dictionary) {

                        if (item == null) {
                            continue;
                        }

                        boolean matched = false;

                        switch (translationMode) {

                            case MODE_ENGLISH_TO_ILOKANO:

                                if (normalizeForExactMatch(
                                        item.getEnglishTranslation()
                                ).equals(
                                        normalizeForExactMatch(clean)
                                )) {

                                    matched = true;

                                    if (item.getIlokanoWord() != null
                                            && !item.getIlokanoWord()
                                            .trim().isEmpty()) {

                                        output = item.getIlokanoWord();
                                    }
                                }

                                break;

                            case MODE_ILOKANO_TO_ENGLISH:

                                if (normalizeForExactMatch(
                                        item.getIlokanoWord()
                                ).equals(
                                        normalizeForExactMatch(clean)
                                )) {

                                    matched = true;

                                    if (item.getEnglishTranslation() != null
                                            && !item.getEnglishTranslation()
                                            .trim().isEmpty()) {

                                        output =
                                                item.getEnglishTranslation();
                                    }
                                }

                                break;

                            case MODE_TAGALOG_TO_ILOKANO:

                                if (normalizeForExactMatch(
                                        item.getTagalogTranslation()
                                ).equals(
                                        normalizeForExactMatch(clean)
                                )) {

                                    matched = true;

                                    if (item.getIlokanoWord() != null
                                            && !item.getIlokanoWord()
                                            .trim().isEmpty()) {

                                        output = item.getIlokanoWord();
                                    }
                                }

                                break;

                            case MODE_ILOKANO_TO_TAGALOG:

                                if (normalizeForExactMatch(
                                        item.getIlokanoWord()
                                ).equals(
                                        normalizeForExactMatch(clean)
                                )) {

                                    matched = true;

                                    if (item.getTagalogTranslation() != null
                                            && !item.getTagalogTranslation()
                                            .trim().isEmpty()) {

                                        output =
                                                item.getTagalogTranslation();
                                    }
                                }

                                break;
                        }

                        if (matched) {
                            break;
                        }
                    }
                }

                translatedParts[i] = output;
            }

            StringBuilder translatedSentence =
                    new StringBuilder();

            for (int i = 0; i < translatedParts.length; i++) {

                if (translatedParts[i] != null) {

                    translatedSentence
                            .append(translatedParts[i])
                            .append(" ");
                }
            }

            String finalTranslation = capitalizeSentence(
                    translatedSentence.toString().trim()
            );

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(
                    () -> {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        showTranslation(finalTranslation);
                    }
            );
        });
    }

    // ---------------------------------------------------------
    // DISPLAY TRANSLATION
    // ---------------------------------------------------------

    private void showTranslation(String translation) {

        if (binding == null || !isAdded()) {
            return;
        }

        binding.translatedText.setText(translation);
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    public void onDestroyView() {

        searchHandler.removeCallbacksAndMessages(null);

        // Dismiss any notification before destroying the view.
        if (currentSnackbar != null) {

            currentSnackbar.dismiss();

            currentSnackbar = null;
        }

        if (tts != null) {

            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }

            tts = null;
        }

        binding = null;

        super.onDestroyView();
    }
}
