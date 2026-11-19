package com.example.speaksmartbaguio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.example.speaksmartbaguio.databinding.FragmentTranslatorBinding;
import com.example.speaksmartbaguio.mapper.EntityMapper;
import com.example.speaksmartbaguio.repository.DictionaryRepository;
import com.example.speaksmartbaguio.repository.PhraseRepository;
import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.AudioPreferences;
import com.example.speaksmartbaguio.utils.NetworkUtil;
import com.example.speaksmartbaguio.utils.SettingsMenuHelper;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class TranslatorFragment extends Fragment {

    private static final String TAG = "TranslatorFragment";
    private static final int STT_REQUEST_CODE = 100;

    private static final String ARROW_SYMBOL = "⇄";
    private static final int ARROW_COLOR = Color.rgb(13, 163, 150);

    private static final int TEAL_COLOR = Color.rgb(13, 163, 150);
    private static final int SNACKBAR_BACKGROUND = Color.rgb(25, 30, 30);

    // Internal markers used to identify POS text before styling.
    private static final char POS_START_MARKER = '\uFFF0';
    private static final char POS_END_MARKER = '\uFFF1';

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
    // Phrasebook matches for the current translation request.
    private List<Phrase> pendingPhraseMatches = new ArrayList<>();

    private String translationMode =
            MODE_ENGLISH_TO_ILOKANO;

    private SharedPreferences translatorPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener translatorPrefListener;

    // ---------------------------------------------------------
    // TRANSLATION ALTERNATIVE MODEL
    // ---------------------------------------------------------

    private static class TranslationAlternative {

        private final String text;
        private final String partOfSpeech;

        TranslationAlternative(
                String text,
                String partOfSpeech
        ) {
            this.text = text;
            this.partOfSpeech = partOfSpeech;
        }
    }

    // ---------------------------------------------------------
    // TEAL POS PILL SPAN
    // ---------------------------------------------------------

    private static class TealPillSpan extends ReplacementSpan {

        private final int backgroundColor;
        private final int textColor;
        private final float horizontalPadding;
        private final float verticalPadding;
        private final float cornerRadius;

        private final Paint backgroundPaint = new Paint(
                Paint.ANTI_ALIAS_FLAG
        );

        private final Paint textPaint = new Paint(
                Paint.ANTI_ALIAS_FLAG
        );

        TealPillSpan(Context context) {

            float density = context.getResources()
                    .getDisplayMetrics()
                    .density;

            backgroundColor = TEAL_COLOR;
            textColor = Color.WHITE;

            horizontalPadding = 7f * density;
            verticalPadding = 3f * density;
            cornerRadius = 10f * density;

            textPaint.setColor(textColor);
            textPaint.setTextSize(11f * density);

            Typeface labelFont = ResourcesCompat.getFont(
                    context,
                    R.font.font_label
            );

            textPaint.setTypeface(
                    labelFont != null
                            ? Typeface.create(labelFont, Typeface.BOLD)
                            : Typeface.create("sans-serif-medium", Typeface.NORMAL)
            );

            textPaint.setAntiAlias(true);

            backgroundPaint.setColor(backgroundColor);
            backgroundPaint.setStyle(Paint.Style.FILL);
            backgroundPaint.setAntiAlias(true);
        }

        @Override
        public int getSize(
                @NonNull Paint paint,
                CharSequence text,
                int start,
                int end,
                @Nullable Paint.FontMetricsInt fm
        ) {

            float textWidth = textPaint.measureText(
                    text,
                    start,
                    end
            );

            if (fm != null) {

                Paint.FontMetricsInt pillMetrics =
                        textPaint.getFontMetricsInt();

                fm.ascent = Math.min(
                        fm.ascent,
                        pillMetrics.ascent
                );

                fm.descent = Math.max(
                        fm.descent,
                        pillMetrics.descent
                );

                fm.ascent -= (int) verticalPadding;
                fm.descent += (int) verticalPadding;
            }

            return (int) Math.ceil(
                    textWidth + (horizontalPadding * 2)
            );
        }

        @Override
        public void draw(
                @NonNull Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                @NonNull Paint paint
        ) {

            float textWidth = textPaint.measureText(
                    text,
                    start,
                    end
            );

            Paint.FontMetrics metrics =
                    textPaint.getFontMetrics();

            float pillWidth =
                    textWidth + (horizontalPadding * 2);

            float pillHeight =
                    (metrics.descent - metrics.ascent)
                            + (verticalPadding * 2);

            float pillTop =
                    y + metrics.ascent - verticalPadding;

            float pillBottom =
                    pillTop + pillHeight;

            RectF pillBounds = new RectF(
                    x,
                    pillTop,
                    x + pillWidth,
                    pillBottom
            );

            canvas.drawRoundRect(
                    pillBounds,
                    cornerRadius,
                    cornerRadius,
                    backgroundPaint
            );

            float textX = x + horizontalPadding;

            float textY = y;

            canvas.drawText(
                    text,
                    start,
                    end,
                    textX,
                    textY,
                    textPaint
            );
        }
    }

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
        registerPreferenceListener();

        AccessibilityPreferences.applyToViewTree(
                requireContext(),
                view
        );
    }

    // ---------------------------------------------------------
    // TRANSLATOR ENTRANCE & UI ANIMATIONS
    // ---------------------------------------------------------

    private void animateTranslatorEntrance(View view) {

        if (view == null) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        view.setAlpha(0f);
        view.setTranslationY(16f * density);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateButtonTap(@Nullable View view, @Nullable Runnable action) {

        if (view == null) {
            if (action != null) {
                action.run();
            }
            return;
        }

        view.animate().cancel();
        view.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(70L)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100L)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start();

                    if (action != null) {
                        action.run();
                    }
                })
                .start();
    }

    // ---------------------------------------------------------
    // SETTINGS MENU
    // ---------------------------------------------------------

    private void setupSettingsMenuButton() {

        binding.buttonSettingsMenu.setOnClickListener(
                v -> animateButtonTap(v, () -> SettingsMenuHelper.show(v))
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

        GradientDrawable background = new GradientDrawable();

        background.setColor(SNACKBAR_BACKGROUND);
        background.setCornerRadius(18f * density);

        snackbarView.setBackground(background);
        snackbarView.setElevation(12f * density);

        TextView messageView = snackbarView.findViewById(
                com.google.android.material.R.id.snackbar_text
        );

        if (messageView != null) {

            messageView.setTextColor(Color.WHITE);
            messageView.setTextSize(14f);
            messageView.setMaxLines(3);

            Typeface bodyFont = ResourcesCompat.getFont(
                    requireContext(),
                    R.font.font_body
            );

            messageView.setTypeface(
                    bodyFont != null
                            ? bodyFont
                            : Typeface.create("sans-serif-medium", Typeface.NORMAL)
            );

            messageView.setGravity(
                    Gravity.CENTER_VERTICAL
            );
        }

        snackbar.setActionTextColor(TEAL_COLOR);

        snackbar.setAnimationMode(
                BaseTransientBottomBar.ANIMATION_MODE_SLIDE
        );

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

                    } else if (tts != null && isAdded()) {

                        AudioPreferences.applyToTextToSpeech(
                                requireContext(),
                                tts
                        );

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

        Typeface bodyFont = ResourcesCompat.getFont(
                context,
                R.font.font_body
        );

        textView.setTypeface(
                bodyFont != null ? bodyFont : Typeface.DEFAULT,
                Typeface.BOLD
        );

        textView.setGravity(Gravity.CENTER_VERTICAL);

        float density = context.getResources()
                .getDisplayMetrics()
                .density;

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

                    binding.titleText.animate().cancel();
                    binding.titleText.setAlpha(0.35f);
                    binding.titleText.setTranslationY(-10f);
                    binding.titleText.setText(
                            getStyledModeText(translationMode)
                    );
                    binding.titleText.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(220L)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();

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
                v -> animateButtonTap(v, this::startSpeechToText)
        );

        binding.ttsButton.setOnClickListener(
                v -> animateButtonTap(v, this::speakTranslation)
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


    private void speakTranslation() {

        if (binding == null) {
            return;
        }

        String text = binding.translatedText
                .getText()
                .toString()
                .trim();

        if (text.isEmpty()) {
            showMessage("Nothing to speak yet.", false);
            return;
        }
        // Remove module headings from speech.
        text = text.replaceAll(
                "(?im)^\\s*(Dictionary\\s*\\(words\\)|Phrasebook\\s*\\(phrases\\)|Did you mean\\?|No exact match found\\.)\\s*$",
                ""
        );
        // Remove POS labels.
        text = text.replaceAll(
                "(?i)\\s*\\(?\\b(noun|verb|adjective|adverb|pronoun|preposition|conjunction|interjection|determiner|article)\\b\\)?",
                ""
        );
        // Remove section headings from speech.
        text = text.replaceAll(
                "(?im)^\\s*(Dictionary\\s*\\(words\\)|Phrasebook\\s*\\(phrases\\))\\s*$",
                ""
        );

        // Remove the visual header accent marker.
        text = text.replace("▌", "");
        // Remove bullet symbols and preserve longer pauses between alternatives.
        text = text.replace("•", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n+ *", ".......... ")
                .trim();

        if (text.isEmpty()) {
            showMessage("Nothing to speak yet.", false);
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

                AudioPreferences.applyToTextToSpeech(
                        requireContext(),
                        tts
                );

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

        if (!isCurrentInput(input) || input.trim().isEmpty()) {
            return;
        }

        // Reset Phrasebook results for every new input.
        pendingPhraseMatches = new ArrayList<>();

        if (!isMultiwordInput(input)) {
            translateDictionaryWordByWord(input);
            return;
        }

        if (NetworkUtil.isOnline(requireContext())) {

            searchOnlinePhrasebook(input);

        } else {

            searchOfflinePhrasebook(input, phrases -> {

                if (!isCurrentInput(input)) {
                    return;
                }

                pendingPhraseMatches =
                        getSuitablePhrases(phrases);

                continueToDictionary(input);
            });
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

        fetchPhrasebookPages(
                input,
                1,
                new ArrayList<>()
        );
    }

    private void fetchPhrasebookPages(
            String input,
            int page,
            List<Phrase> collected
    ) {

        if (!isCurrentInput(input)) {
            return;
        }

        apiService.getPhrasebook(
                page,
                100,
                "",
                new ApiService.ApiCallback<Phrase>() {

                    @Override
                    public void onSuccess(
                            List<Phrase> results,
                            boolean hasMore
                    ) {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        if (results != null && !results.isEmpty()) {
                            collected.addAll(results);
                        }

                        if (hasMore
                                && results != null
                                && !results.isEmpty()) {

                            fetchPhrasebookPages(
                                    input,
                                    page + 1,
                                    collected
                            );

                            return;
                        }

                        List<Phrase> exactMatches =
                                findBestPhraseMatches(
                                        input,
                                        collected
                                );

                        List<Phrase> suitable =
                                getSuitablePhrases(exactMatches);

                        if (!suitable.isEmpty()) {

                            displayPhraseTranslations(
                                    input,
                                    suitable
                            );

                        } else {

                            searchOfflinePhrasebook(
                                    input,
                                    phrases -> {

                                        if (!isCurrentInput(input)) {
                                            return;
                                        }

                                        List<Phrase> offlineMatches =
                                                getSuitablePhrases(phrases);

                                        if (!offlineMatches.isEmpty()) {

                                            displayPhraseTranslations(
                                                    input,
                                                    offlineMatches
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
                                phrases -> {

                                    if (!isCurrentInput(input)) {
                                        return;
                                    }

                                    List<Phrase> suitable =
                                            getSuitablePhrases(phrases);

                                    if (!suitable.isEmpty()) {

                                        displayPhraseTranslations(
                                                input,
                                                suitable
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
        void onResult(@Nullable List<Phrase> phrases);
    }

    private void searchOfflinePhrasebook(
            String input,
            PhraseLookupCallback callback
    ) {

        if (phraseRepository == null) {

            callback.onResult(new ArrayList<>());

            return;
        }

        phraseRepository.searchPhrases(
                input,
                entities -> {

                    List<Phrase> phrases =
                            EntityMapper.toPhraseList(entities);

                    List<Phrase> exactMatches =
                            findBestPhraseMatches(
                                    input,
                                    phrases
                            );

                    if (!exactMatches.isEmpty()) {

                        callback.onResult(exactMatches);

                        return;
                    }

                    phraseRepository.getAllPhrases(
                            allEntities -> {

                                List<Phrase> allPhrases =
                                        EntityMapper.toPhraseList(
                                                allEntities
                                        );

                                callback.onResult(
                                        findBestPhraseMatches(
                                                input,
                                                allPhrases
                                        )
                                );
                            }
                    );
                }
        );
    }


    // ---------------------------------------------------------
    // FIND EXACT OR NEAREST PHRASEBOOK MATCHES
    // ---------------------------------------------------------

    private List<Phrase> findBestPhraseMatches(
            String input,
            @Nullable List<Phrase> phrases
    ) {

        List<Phrase> exactMatches = new ArrayList<>();

        if (input == null || phrases == null || phrases.isEmpty()) {
            return exactMatches;
        }

        String normalizedInput = normalizeForExactMatch(input);

        if (normalizedInput.isEmpty()) {
            return exactMatches;
        }

        // PRIORITY 1: Exact matches
        for (Phrase phrase : phrases) {

            if (phrase == null) {
                continue;
            }

            if (getPhraseMatchScore(normalizedInput, phrase) >= 1.0) {
                exactMatches.add(phrase);
            }
        }

        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        // PRIORITY 2: Find the nearest available phrase
        double highestScore = 0.0;

        List<Phrase> nearestMatches = new ArrayList<>();

        for (Phrase phrase : phrases) {

            if (phrase == null || !isSuitablePhrase(phrase)) {
                continue;
            }

            double score = getPhraseMatchScore(
                    normalizedInput,
                    phrase
            );

            if (score > highestScore) {

                highestScore = score;

                nearestMatches.clear();
                nearestMatches.add(phrase);

            } else if (score > 0.0
                    && Math.abs(score - highestScore) < 0.0001) {

                nearestMatches.add(phrase);
            }
        }

        // Reject weak or unrelated matches.
        if (highestScore < 0.72) {
            return new ArrayList<>();
        }

        return nearestMatches;
    }


    // ---------------------------------------------------------
    // GET THE BEST MATCH SCORE FOR A PHRASE
    // ---------------------------------------------------------

    private double getPhraseMatchScore(
            String normalizedInput,
            Phrase phrase
    ) {

        double bestScore = 0.0;

        switch (translationMode) {

            case MODE_ENGLISH_TO_ILOKANO:

                bestScore = Math.max(
                        calculatePhraseSimilarity(
                                normalizedInput,
                                phrase.getEnglishSentence()
                        ),
                        calculatePhraseSimilarity(
                                normalizedInput,
                                phrase.getEnglishTranslation()
                        )
                );

                break;

            case MODE_TAGALOG_TO_ILOKANO:

                bestScore = calculatePhraseSimilarity(
                        normalizedInput,
                        phrase.getTagalogTranslation()
                );

                break;

            case MODE_ILOKANO_TO_ENGLISH:
            case MODE_ILOKANO_TO_TAGALOG:

                bestScore = Math.max(
                        calculatePhraseSimilarity(
                                normalizedInput,
                                phrase.getIlokanoSentence()
                        ),
                        calculatePhraseSimilarity(
                                normalizedInput,
                                phrase.getIlokanoWord()
                        )
                );

                break;
        }

        return bestScore;
    }


    // ---------------------------------------------------------
    // CALCULATE PHRASE SIMILARITY
    // ---------------------------------------------------------

    private double calculatePhraseSimilarity(
            String normalizedInput,
            String candidate
    ) {

        String normalizedCandidate =
                normalizeForExactMatch(candidate);

        if (normalizedInput.isEmpty()
                || normalizedCandidate.isEmpty()) {
            return 0.0;
        }

        // Exact match always receives the highest score.
        if (normalizedInput.equals(normalizedCandidate)) {
            return 1.0;
        }

        String[] inputWords = normalizedInput.split("\\s+");
        String[] candidateWords = normalizedCandidate.split("\\s+");

        boolean[] matchedCandidateWords =
                new boolean[candidateWords.length];

        int matchedWords = 0;

        // Match words without counting the same candidate word twice.
        for (String inputWord : inputWords) {

            for (int i = 0; i < candidateWords.length; i++) {

                if (matchedCandidateWords[i]) {
                    continue;
                }

                if (inputWord.equals(candidateWords[i])) {

                    matchedCandidateWords[i] = true;
                    matchedWords++;

                    break;
                }
            }
        }

        // Dice similarity: considers both missing and extra words.
        return (2.0 * matchedWords)
                / (inputWords.length + candidateWords.length);
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

    private List<Phrase> getSuitablePhrases(
            @Nullable List<Phrase> phrases
    ) {

        List<Phrase> suitable = new ArrayList<>();

        if (phrases == null) {
            return suitable;
        }

        for (Phrase phrase : phrases) {

            if (isSuitablePhrase(phrase)) {
                suitable.add(phrase);
            }
        }

        return suitable;
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
    // DISPLAY ALL MATCHED PHRASES
    // ---------------------------------------------------------


    private void displayPhraseTranslations(
            String input,
            List<Phrase> phrases
    ) {

        if (!isCurrentInput(input)) {
            return;
        }

        pendingPhraseMatches =
                getSuitablePhrases(phrases);

        continueToDictionary(input);
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

        if (!isCurrentInput(input)) {
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

        final String[] translatedParts =
                new String[words.length];

        final int[] completed = {0};

        final int totalWords = words.length;

        for (int i = 0; i < totalWords; i++) {

            final int wordIndex = i;

            final String originalWord = words[i];

            String cleanedWord = originalWord
                    .replaceAll("[^\\p{L}\\p{N}]", "")
                    .toLowerCase(Locale.ROOT);

            if (cleanedWord.isEmpty()) {

                translatedParts[wordIndex] = originalWord;

                completeOnlineWord(
                        input,
                        translatedParts,
                        completed,
                        totalWords
                );

                continue;
            }

            String fieldSearch = getDictionarySearchField();

            fetchAllDictionaryMatches(
                    input,
                    cleanedWord,
                    fieldSearch,
                    1,
                    new ArrayList<>(),
                    matches -> {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        List<TranslationAlternative> alternatives =
                                getDictionaryAlternatives(matches);

                        translatedParts[wordIndex] =
                                alternatives.isEmpty()
                                        ? ""
                                        : formatWordAlternatives(
                                        originalWord,
                                        alternatives,
                                        totalWords == 1
                                );

                        completeOnlineWord(
                                input,
                                translatedParts,
                                completed,
                                totalWords
                        );
                    }
            );
        }
    }

    // ---------------------------------------------------------
    // DICTIONARY SEARCH FIELD
    // ---------------------------------------------------------

    private String getDictionarySearchField() {

        switch (translationMode) {

            case MODE_ENGLISH_TO_ILOKANO:
                return "englishTranslation";

            case MODE_ILOKANO_TO_ENGLISH:
            case MODE_ILOKANO_TO_TAGALOG:
                return "ilokanoWord";

            case MODE_TAGALOG_TO_ILOKANO:
                return "tagalogTranslation";

            default:
                return "englishTranslation";
        }
    }

    // ---------------------------------------------------------
    // FETCH ALL DICTIONARY PAGES
    // ---------------------------------------------------------

    private interface DictionaryLookupCallback {
        void onResult(List<Word> words);
    }

    private void fetchAllDictionaryMatches(
            String input,
            String searchValue,
            String field,
            int page,
            List<Word> collected,
            DictionaryLookupCallback callback
    ) {

        if (!isCurrentInput(input)) {
            return;
        }

        apiService.searchDictionaryWord(
                field,
                searchValue,
                page,
                100,
                new ApiService.ApiCallback<Word>() {

                    @Override
                    public void onSuccess(
                            List<Word> results,
                            boolean hasMore
                    ) {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        if (results != null && !results.isEmpty()) {
                            collected.addAll(results);
                        }

                        if (hasMore
                                && results != null
                                && !results.isEmpty()) {

                            fetchAllDictionaryMatches(
                                    input,
                                    searchValue,
                                    field,
                                    page + 1,
                                    collected,
                                    callback
                            );

                        } else {

                            callback.onResult(
                                    filterExactDictionaryMatches(
                                            searchValue,
                                            field,
                                            collected
                                    )
                            );
                        }
                    }

                    @Override
                    public void onError(String error) {

                        if (!isCurrentInput(input)) {
                            return;
                        }

                        Log.e(
                                TAG,
                                "Dictionary search failed for: "
                                        + searchValue
                                        + " - "
                                        + error
                        );

                        callback.onResult(
                                filterExactDictionaryMatches(
                                        searchValue,
                                        field,
                                        collected
                                )
                        );
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // FILTER EXACT DICTIONARY MATCHES
    // ---------------------------------------------------------

    private List<Word> filterExactDictionaryMatches(
            String searchValue,
            String field,
            List<Word> words
    ) {

        List<Word> exactMatches = new ArrayList<>();

        if (words == null) {
            return exactMatches;
        }

        String normalizedSearch =
                normalizeForExactMatch(searchValue);

        for (Word word : words) {

            if (word == null) {
                continue;
            }

            String candidate;

            switch (field) {

                case "englishTranslation":
                    candidate = word.getEnglishTranslation();
                    break;

                case "ilokanoWord":
                    candidate = word.getIlokanoWord();
                    break;

                case "tagalogTranslation":
                    candidate = word.getTagalogTranslation();
                    break;

                default:
                    candidate = "";
                    break;
            }

            if (normalizedSearch.equals(
                    normalizeForExactMatch(candidate)
            )) {

                exactMatches.add(word);
            }
        }

        return exactMatches;
    }

    // ---------------------------------------------------------
    // GET DISTINCT TRANSLATIONS WITH PART OF SPEECH
    // ---------------------------------------------------------

    private List<TranslationAlternative> getDictionaryAlternatives(
            List<Word> matches
    ) {

        List<TranslationAlternative> alternatives =
                new ArrayList<>();

        LinkedHashSet<String> seen = new LinkedHashSet<>();

        if (matches == null) {
            return alternatives;
        }

        for (Word word : matches) {

            if (word == null) {
                continue;
            }

            String output = null;

            switch (translationMode) {

                case MODE_ENGLISH_TO_ILOKANO:
                case MODE_TAGALOG_TO_ILOKANO:

                    output = word.getIlokanoWord();
                    break;

                case MODE_ILOKANO_TO_ENGLISH:

                    output = word.getEnglishTranslation();
                    break;

                case MODE_ILOKANO_TO_TAGALOG:

                    output = word.getTagalogTranslation();
                    break;
            }

            if (output == null || output.trim().isEmpty()) {
                continue;
            }

            output = output.trim();

            String pos = word.getPartOfSpeech();

            if (pos == null) {
                pos = "";
            }

            pos = pos.trim();

            String uniqueKey =
                    normalizeForExactMatch(output)
                            + "|"
                            + pos.toLowerCase(Locale.ROOT);

            if (seen.add(uniqueKey)) {

                alternatives.add(
                        new TranslationAlternative(
                                output,
                                pos
                        )
                );
            }
        }

        return alternatives;
    }

    // ---------------------------------------------------------
    // FORMAT POS MARKER
    // ---------------------------------------------------------

    private String appendPosMarker(
            String translation,
            String partOfSpeech
    ) {

        if (partOfSpeech == null
                || partOfSpeech.trim().isEmpty()) {

            return translation;
        }

        return translation
                + " "
                + POS_START_MARKER
                + partOfSpeech.trim()
                + POS_END_MARKER;
    }

    // ---------------------------------------------------------
    // FORMAT DICTIONARY WORD ALTERNATIVES WITH POS
    // ---------------------------------------------------------


    private String formatWordAlternatives(
            String originalWord,
            List<TranslationAlternative> alternatives,
            boolean singleWord
    ) {

        if (alternatives == null || alternatives.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        // Always display the original word first.
        result.append(originalWord).append(":\n");

        for (int i = 0; i < alternatives.size(); i++) {

            TranslationAlternative alternative =
                    alternatives.get(i);

            if (i > 0) {
                result.append("\n\n");
            }

            result.append("•  ");

            result.append(
                    appendPosMarker(
                            capitalizeSentence(alternative.text),
                            alternative.partOfSpeech
                    )
            );
        }

        return result.toString();
    }


    // ---------------------------------------------------------
    // FORMAT PHRASEBOOK ALTERNATIVES
    // ---------------------------------------------------------

    private String formatAlternativeList(List<String> alternatives) {

        if (alternatives == null || alternatives.isEmpty()) {
            return "";
        }

        if (alternatives.size() == 1) {
            return capitalizeSentence(alternatives.get(0));
        }

        StringBuilder result = new StringBuilder();

        for (String alternative : alternatives) {

            if (alternative == null || alternative.trim().isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append("\n\n");
            }

            result.append("•  ")
                    .append(capitalizeSentence(alternative.trim()));
        }

        return result.toString();
    }

    // ---------------------------------------------------------
    // COMPLETE ONLINE WORD LOOKUPS
    // ---------------------------------------------------------


    private void completeOnlineWord(
            String input,
            String[] translatedParts,
            int[] completed,
            int totalWords
    ) {

        if (!isCurrentInput(input)) {
            return;
        }

        completed[0]++;

        if (completed[0] < totalWords) {
            return;
        }

        StringBuilder dictionaryResults =
                new StringBuilder();

        for (String translatedPart : translatedParts) {

            if (translatedPart != null
                    && !translatedPart.trim().isEmpty()) {

                if (dictionaryResults.length() > 0) {
                    dictionaryResults.append("\n\n");
                }

                dictionaryResults.append(translatedPart);
            }
        }

        String grouped = buildGroupedTranslation(
                dictionaryResults.toString()
        );

        if ("No translation found.".equals(grouped)) {
            suggestNearestFromDatabase(input);
        } else {
            showTranslation(grouped);
        }
    }


    // ---------------------------------------------------------
    // OFFLINE DICTIONARY TRANSLATION
    // ---------------------------------------------------------

    private void translateOffline(String input) {

        if (!isCurrentInput(input)) {
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

            String searchField = getDictionarySearchField();

            for (int i = 0; i < words.length; i++) {

                String originalWord = words[i];

                String clean = originalWord
                        .replaceAll("[^\\p{L}\\p{N}]", "")
                        .toLowerCase(Locale.ROOT);

                if (clean.isEmpty()) {

                    translatedParts[i] = originalWord;

                    continue;
                }

                List<Word> matches =
                        filterExactDictionaryMatches(
                                clean,
                                searchField,
                                dictionary
                        );

                List<TranslationAlternative> alternatives =
                        getDictionaryAlternatives(matches);

                translatedParts[i] =
                        alternatives.isEmpty()
                                ? ""
                                : formatWordAlternatives(
                                originalWord,
                                alternatives,
                                words.length == 1
                        );
            }

            StringBuilder translatedSentence =
                    new StringBuilder();

            for (String part : translatedParts) {

                if (part != null && !part.trim().isEmpty()) {

                    if (translatedSentence.length() > 0) {
                        translatedSentence.append("\n\n");
                    }

                    translatedSentence.append(part);
                }
            }

            String finalTranslation =
                    buildGroupedTranslation(
                            translatedSentence.toString().trim()
                    );

            if ("No translation found.".equals(finalTranslation)) {
                suggestNearestFromDatabase(input);
                return;
            }

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {

                if (!isCurrentInput(input)) {
                    return;
                }

                showTranslation(finalTranslation);
            });
        });
    }

    // ---------------------------------------------------------
    // BUILD STYLED TRANSLATION WITH TEAL POS PILLS
    // ---------------------------------------------------------


    private CharSequence buildStyledTranslation(String translation) {

        if (translation == null || translation.isEmpty()) {
            return "";
        }

        SpannableStringBuilder styled =
                new SpannableStringBuilder();

        int currentIndex = 0;

        // -----------------------------------------------------
        // PROCESS EXISTING POS MARKERS
        // -----------------------------------------------------

        while (currentIndex < translation.length()) {

            int markerStart = translation.indexOf(
                    POS_START_MARKER,
                    currentIndex
            );

            if (markerStart < 0) {

                styled.append(
                        translation.substring(currentIndex)
                );

                break;
            }

            styled.append(
                    translation.substring(
                            currentIndex,
                            markerStart
                    )
            );

            int markerEnd = translation.indexOf(
                    POS_END_MARKER,
                    markerStart + 1
            );

            if (markerEnd < 0) {

                styled.append(
                        translation.substring(markerStart)
                );

                break;
            }

            String pos = translation.substring(
                    markerStart + 1,
                    markerEnd
            );

            if (!pos.trim().isEmpty()) {

                int spanStart = styled.length();

                styled.append(pos);

                int spanEnd = styled.length();

                if (isAdded()) {

                    styled.setSpan(
                            new TealPillSpan(requireContext()),
                            spanStart,
                            spanEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }

            currentIndex = markerEnd + 1;
        }

        // -----------------------------------------------------
        // STYLE DICTIONARY AND PHRASEBOOK SECTION HEADERS
        // -----------------------------------------------------

        final String[] headers = {
                "Dictionary (words)",
                "Phrasebook (phrases)",
                "Did you mean?"
        };

        // Process from bottom to top so inserted markers
        // do not affect the positions of earlier headers.
        for (int i = styled.length() - 1; i >= 0; i--) {

            int lineStart = i;

            while (lineStart > 0
                    && styled.charAt(lineStart - 1) != '\n') {
                lineStart--;
            }

            int lineEnd = i;

            while (lineEnd < styled.length()
                    && styled.charAt(lineEnd) != '\n') {
                lineEnd++;
            }

            String line = styled.subSequence(
                    lineStart,
                    lineEnd
            ).toString().trim();

            boolean isHeader = false;

            for (String header : headers) {

                if (line.equalsIgnoreCase(header)) {
                    isHeader = true;
                    break;
                }
            }

            if (!isHeader) {
                i = lineStart;
                continue;
            }

            // Find the actual beginning of the header text.
            int titleStart = lineStart;

            while (titleStart < lineEnd
                    && Character.isWhitespace(
                    styled.charAt(titleStart)
            )) {
                titleStart++;
            }

            // Add a teal accent marker.
            styled.insert(titleStart, "▌ ");

            int markerStart = titleStart;
            int titleTextStart = titleStart + 2;
            int titleEnd = titleTextStart + line.length();

            // Teal accent marker.
            styled.setSpan(
                    new ForegroundColorSpan(TEAL_COLOR),
                    markerStart,
                    titleTextStart,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            // Header text styling.
            styled.setSpan(
                    new ForegroundColorSpan(TEAL_COLOR),
                    titleTextStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            styled.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    titleTextStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            styled.setSpan(
                    new RelativeSizeSpan(1.12f),
                    titleTextStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            // Subtle teal background behind the complete header.
            styled.setSpan(
                    new BackgroundColorSpan(
                            Color.argb(28, 13, 163, 150)
                    ),
                    markerStart,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            i = lineStart;
        }

        return styled;
    }


    private String buildGroupedTranslation(
            String dictionaryResults
    ) {

        StringBuilder result = new StringBuilder();

        // DICTIONARY SECTION
        if (dictionaryResults != null
                && !dictionaryResults.trim().isEmpty()) {

            result.append("Dictionary (words)\n\n");
            result.append(dictionaryResults.trim());
        }

        // PHRASEBOOK SECTION
        LinkedHashSet<String> uniquePhrases =
                new LinkedHashSet<>();

        if (pendingPhraseMatches != null) {

            for (Phrase phrase : pendingPhraseMatches) {

                String output = getPhraseOutput(phrase);

                if (output != null && !output.trim().isEmpty()) {
                    uniquePhrases.add(
                            capitalizeSentence(output.trim())
                    );
                }
            }
        }

        if (!uniquePhrases.isEmpty()) {

            if (result.length() > 0) {
                result.append("\n\n");
            }

            result.append("Phrasebook (phrases)\n\n");

            int index = 0;

            for (String phrase : uniquePhrases) {

                if (index > 0) {
                    result.append("\n\n");
                }

                if (uniquePhrases.size() > 1) {
                    result.append("•  ");
                }

                result.append(phrase);

                index++;
            }
        }

        if (result.length() == 0) {
            return "No translation found.";
        }

        return result.toString();
    }

    // ---------------------------------------------------------
    // DISPLAY TRANSLATION
    // ---------------------------------------------------------

    private void showTranslation(String translation) {

        if (binding == null || !isAdded()) {
            return;
        }

        binding.translatedText.setText(
                buildStyledTranslation(translation)
        );

        float density = getResources().getDisplayMetrics().density;
        binding.translatedText.animate().cancel();
        binding.translatedText.setAlpha(0f);
        binding.translatedText.setTranslationY(10f * density);
        binding.translatedText.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    public void onDestroyView() {

        searchHandler.removeCallbacksAndMessages(null);

        if (currentSnackbar != null) {

            currentSnackbar.dismiss();

            currentSnackbar = null;
        }

        if (binding != null) {
            binding.getRoot().animate().cancel();
            binding.translatedText.animate().cancel();
            binding.titleText.animate().cancel();
            binding.micButton.animate().cancel();
            binding.ttsButton.animate().cancel();
            binding.buttonSettingsMenu.animate().cancel();
        }

        if (tts != null) {

            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }

            tts = null;
        }

        if (translatorPrefs != null && translatorPrefListener != null) {
            translatorPrefs.unregisterOnSharedPreferenceChangeListener(translatorPrefListener);
            translatorPrefListener = null;
            translatorPrefs = null;
        }

        binding = null;

        super.onDestroyView();
    }

    private void registerPreferenceListener() {
        translatorPrefs = requireContext().getSharedPreferences(
                "TranslatorPrefs",
                Context.MODE_PRIVATE
        );

        translatorPrefListener = (sharedPreferences, key) -> {
            if (!"translationMode".equals(key) || binding == null || !isAdded()) {
                return;
            }

            String updatedMode = sharedPreferences.getString(
                    "translationMode",
                    MODE_ENGLISH_TO_ILOKANO
            );

            if (updatedMode == null || updatedMode.equals(translationMode)) {
                return;
            }

            translationMode = updatedMode;
            binding.languageDropdown.setText(
                    getStyledModeText(translationMode),
                    false
            );
            binding.titleText.setText(
                    getStyledModeText(translationMode)
            );
            setTTSLanguage();
        };

        translatorPrefs.registerOnSharedPreferenceChangeListener(translatorPrefListener);
    }

    // ---------------------------------------------------------
    // FUZZY DATABASE SUGGESTIONS WHEN NO EXACT MATCH IS FOUND
    // ---------------------------------------------------------

    private static class SuggestionItem {
        final String source;
        final String target;
        final String partOfSpeech;
        final double score;

        SuggestionItem(String source, String target, String partOfSpeech, double score) {
            this.source = source;
            this.target = target;
            this.partOfSpeech = partOfSpeech != null ? partOfSpeech.trim() : "";
            this.score = score;
        }
    }

    private void suggestNearestFromDatabase(String input) {
        if (!isCurrentInput(input) || repository == null) {
            showTranslation("No translation found.");
            return;
        }

        repository.getAllWords(wordEntities -> {
            if (!isCurrentInput(input)) {
                return;
            }

            List<Word> allWords = EntityMapper.toWordList(wordEntities);

            if (phraseRepository == null) {
                deliverDatabaseSuggestions(input, allWords, new ArrayList<>());
                return;
            }

            phraseRepository.getAllPhrases(phraseEntities -> {
                if (!isCurrentInput(input)) {
                    return;
                }

                List<Phrase> allPhrases = EntityMapper.toPhraseList(phraseEntities);
                deliverDatabaseSuggestions(input, allWords, allPhrases);
            });
        });
    }

    private void deliverDatabaseSuggestions(
            String input,
            @Nullable List<Word> allWords,
            @Nullable List<Phrase> allPhrases
    ) {
        String resultText = buildDatabaseSuggestionsText(input, allWords, allPhrases);

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            if (!isCurrentInput(input)) {
                return;
            }
            showTranslation(resultText);
        });
    }

    private String buildDatabaseSuggestionsText(
            String input,
            @Nullable List<Word> allWords,
            @Nullable List<Phrase> allPhrases
    ) {
        String normalizedInput = normalizeForExactMatch(input);
        if (normalizedInput.isEmpty()) {
            return "No translation found.";
        }

        List<SuggestionItem> suggestions = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        if (allWords != null) {
            for (Word word : allWords) {
                if (word == null) continue;
                String source = getWordSource(word);
                String target = getWordOutput(word);
                if (source == null || target == null) continue;

                double score = scoreFuzzyMatch(normalizedInput, source);
                if (score > 0.0) {
                    String key = normalizeForExactMatch(source) + "|" + normalizeForExactMatch(target);
                    if (seen.add(key)) {
                        suggestions.add(new SuggestionItem(
                                source,
                                target,
                                word.getPartOfSpeech(),
                                score
                        ));
                    }
                }
            }
        }

        if (allPhrases != null) {
            for (Phrase phrase : allPhrases) {
                if (phrase == null) continue;
                String source = getPhraseSource(phrase);
                String target = getPhraseOutput(phrase);
                if (source == null || target == null) continue;

                double fuzzyScore = scoreFuzzyMatch(normalizedInput, source);
                double diceScore = calculatePhraseSimilarity(normalizedInput, source);
                double bestScore = Math.max(fuzzyScore, diceScore >= 0.35 ? diceScore : -1.0);

                if (bestScore > 0.0) {
                    String key = normalizeForExactMatch(source) + "|" + normalizeForExactMatch(target);
                    if (seen.add(key)) {
                        suggestions.add(new SuggestionItem(
                                source,
                                target,
                                phrase.getPartOfSpeech(),
                                bestScore
                        ));
                    }
                }
            }
        }

        if (suggestions.isEmpty()) {
            return "No translation found.";
        }

        Collections.sort(suggestions, (a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            return cmp != 0 ? cmp : a.source.compareToIgnoreCase(b.source);
        });

        StringBuilder sb = new StringBuilder();
        sb.append("No exact match found.\n\n");
        sb.append("Did you mean?\n\n");

        for (int i = 0; i < suggestions.size(); i++) {
            SuggestionItem item = suggestions.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("•  ")
                    .append(capitalizeSentence(item.source))
                    .append("  →  ")
                    .append(appendPosMarker(
                            capitalizeSentence(item.target),
                            item.partOfSpeech
                    ));
        }

        return sb.toString();
    }

    @Nullable
    private String getWordSource(Word word) {
        if (word == null) return null;
        String value;
        switch (translationMode) {
            case MODE_ENGLISH_TO_ILOKANO:
                value = word.getEnglishTranslation();
                break;
            case MODE_TAGALOG_TO_ILOKANO:
                value = word.getTagalogTranslation();
                break;
            case MODE_ILOKANO_TO_ENGLISH:
            case MODE_ILOKANO_TO_TAGALOG:
                value = word.getIlokanoWord();
                break;
            default:
                value = null;
                break;
        }
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Nullable
    private String getWordOutput(Word word) {
        if (word == null) return null;
        String value;
        switch (translationMode) {
            case MODE_ENGLISH_TO_ILOKANO:
            case MODE_TAGALOG_TO_ILOKANO:
                value = word.getIlokanoWord();
                break;
            case MODE_ILOKANO_TO_ENGLISH:
                value = word.getEnglishTranslation();
                break;
            case MODE_ILOKANO_TO_TAGALOG:
                value = word.getTagalogTranslation();
                break;
            default:
                value = null;
                break;
        }
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    @Nullable
    private String getPhraseSource(Phrase phrase) {
        if (phrase == null) return null;
        String value;
        switch (translationMode) {
            case MODE_ENGLISH_TO_ILOKANO:
                value = phrase.getEnglishSentence();
                if (value == null || value.trim().isEmpty()) {
                    value = phrase.getEnglishTranslation();
                }
                break;
            case MODE_TAGALOG_TO_ILOKANO:
                value = phrase.getTagalogTranslation();
                break;
            case MODE_ILOKANO_TO_ENGLISH:
            case MODE_ILOKANO_TO_TAGALOG:
                value = phrase.getIlokanoSentence();
                if (value == null || value.trim().isEmpty()) {
                    value = phrase.getIlokanoWord();
                }
                break;
            default:
                value = null;
                break;
        }
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private double scoreFuzzyMatch(String normalizedInput, String rawCandidate) {
        String normalizedCandidate = normalizeForExactMatch(rawCandidate);
        if (normalizedInput.isEmpty() || normalizedCandidate.isEmpty()) {
            return -1.0;
        }
        if (normalizedCandidate.equals(normalizedInput)) {
            return 1.0;
        }
        if (normalizedCandidate.startsWith(normalizedInput)) {
            return 0.95;
        }
        if (normalizedCandidate.contains(normalizedInput)) {
            return 0.85;
        }
        if (normalizedInput.contains(normalizedCandidate) && normalizedCandidate.length() >= 3) {
            return 0.80;
        }

        int maxLen = Math.max(normalizedInput.length(), normalizedCandidate.length());
        int maxAllowedDist = maxLen <= 3 ? 1 : Math.max(2, maxLen / 3);
        int dist = levenshteinDistance(normalizedInput, normalizedCandidate);
        if (dist <= maxAllowedDist) {
            return 1.0 - ((double) dist / maxLen);
        }

        // Also check individual words inside a multi-word input or candidate.
        String[] inputWords = normalizedInput.split("\\s+");
        String[] candidateWords = normalizedCandidate.split("\\s+");
        double bestWordScore = -1.0;
        for (String iw : inputWords) {
            if (iw.length() < 2) continue;
            for (String cw : candidateWords) {
                if (cw.length() < 2) continue;
                if (cw.startsWith(iw)) {
                    bestWordScore = Math.max(bestWordScore, 0.72);
                    continue;
                }
                int wMax = Math.max(iw.length(), cw.length());
                int wAllowed = wMax <= 3 ? 1 : Math.max(2, wMax / 3);
                int wDist = levenshteinDistance(iw, cw);
                if (wDist <= wAllowed) {
                    double s = 0.75 * (1.0 - ((double) wDist / wMax));
                    bestWordScore = Math.max(bestWordScore, s);
                }
            }
        }
        return bestWordScore;
    }

    private int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lenB; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[lenB];
    }
}
