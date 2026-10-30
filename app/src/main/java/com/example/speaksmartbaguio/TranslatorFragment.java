package com.example.speaksmartbaguio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
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
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

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

import com.google.firebase.functions.FirebaseFunctions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TranslatorFragment extends Fragment {

    private static final String TAG = "TranslatorFragment";
    private static final int STT_REQUEST_CODE = 100;

    private static final String ARROW_SYMBOL = "⇄";
    private static final int ARROW_COLOR = Color.rgb(13, 163, 150);

    private static final String MODE_ENGLISH_TO_ILOKANO =
            "English  ⇄  Ilokano";

    private static final String MODE_ILOKANO_TO_ENGLISH =
            "Ilokano  ⇄  English";

    private static final String MODE_TAGALOG_TO_ILOKANO =
            "Tagalog  ⇄  Ilokano";

    private static final String MODE_ILOKANO_TO_TAGALOG =
            "Ilokano  ⇄  Tagalog";

    private static final String TTS_FUNCTION_NAME =
            "synthesizeSpeech";

    private FragmentTranslatorBinding binding;

    private ApiService apiService;
    private DictionaryRepository repository;
    private PhraseRepository phraseRepository;
    private TextToSpeech tts;

    private FirebaseFunctions firebaseFunctions;

    private MediaPlayer pronunciationPlayer;
    private MediaPlayer cloudTtsPlayer;

    private File cloudTtsFile;

    private int cloudTtsRequestId = 0;

    private final List<String> currentIlokanoAudioUrls =
            new ArrayList<>();

    private int currentAudioIndex = 0;
    private boolean audioPlaybackHadError = false;

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

        firebaseFunctions = FirebaseFunctions.getInstance(
                "asia-southeast1"
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
    // ANDROID TEXT-TO-SPEECH
    // ---------------------------------------------------------

    private void setupTextToSpeech() {

        tts = new TextToSpeech(
                requireContext(),
                status -> {

                    if (status != TextToSpeech.SUCCESS) {

                        if (isAdded() && getContext() != null) {

                            Toast.makeText(
                                    requireContext(),
                                    "Android TTS initialization failed",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

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

        textView.setTextColor(Color.WHITE);
        textView.setTextSize(15f);
        textView.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
        );

        textView.setGravity(Gravity.CENTER_VERTICAL);

        int horizontalPadding = (int) (
                16 * context.getResources().getDisplayMetrics().density
        );

        int verticalPadding = (int) (
                12 * context.getResources().getDisplayMetrics().density
        );

        textView.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        if (isDropdownItem) {

            textView.setMinHeight(
                    (int) (
                            48 * context.getResources()
                                    .getDisplayMetrics()
                                    .density
                    )
            );
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

                    cancelCloudTtsSpeech();
                    stopTextToSpeech();
                    stopIlokanoAudio();

                    currentIlokanoAudioUrls.clear();

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

        if (isIlokanoOutputMode()) {
            return;
        }

        Locale locale;

        switch (translationMode) {

            case MODE_ILOKANO_TO_ENGLISH:
                locale = Locale.US;
                break;

            case MODE_ILOKANO_TO_TAGALOG:
                locale = new Locale("fil", "PH");
                break;

            default:
                locale = Locale.US;
                break;
        }

        int result = tts.setLanguage(locale);

        if (result == TextToSpeech.LANG_NOT_SUPPORTED
                || result == TextToSpeech.LANG_MISSING_DATA) {

            if (isAdded() && getContext() != null) {

                Toast.makeText(
                        requireContext(),
                        "Android TTS language is not supported. "
                                + "Cloud speech may still be available.",
                        Toast.LENGTH_LONG
                ).show();
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

                        cancelCloudTtsSpeech();
                        stopIlokanoAudio();
                        stopTextToSpeech();

                        currentIlokanoAudioUrls.clear();

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

            Toast.makeText(
                    requireContext(),
                    "Speech recognition not supported",
                    Toast.LENGTH_SHORT
            ).show();
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

            Toast.makeText(
                    requireContext(),
                    "Nothing to speak",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (isIlokanoOutputMode()) {

            cancelCloudTtsSpeech();
            stopTextToSpeech();

            playIlokanoRecordings();

            return;
        }

        stopIlokanoAudio();

        cancelCloudTtsSpeech();

        final int requestId = cloudTtsRequestId;

        if (getContext() == null
                || !NetworkUtil.isOnline(requireContext())) {

            speakWithAndroidTts(text);

            return;
        }

        requestCloudSpeech(text, requestId);
    }

    // ---------------------------------------------------------
    // GOOGLE CLOUD TEXT-TO-SPEECH
    // ---------------------------------------------------------

    private void requestCloudSpeech(
            String text,
            int requestId
    ) {

        if (binding == null
                || !isAdded()
                || firebaseFunctions == null
                || requestId != cloudTtsRequestId) {

            return;
        }

        String language;

        if (MODE_ILOKANO_TO_TAGALOG.equals(translationMode)) {
            language = "tl";
        } else {
            language = "en";
        }

        Map<String, Object> data = new HashMap<>();

        data.put("text", text);
        data.put("language", language);

        firebaseFunctions
                .getHttpsCallable(TTS_FUNCTION_NAME)
                .call(data)
                .addOnSuccessListener(result -> {

                    if (!isAdded()
                            || binding == null
                            || requestId != cloudTtsRequestId) {

                        return;
                    }

                    try {

                        Object rawData = result.getData();

                        if (!(rawData instanceof Map)) {

                            throw new IllegalStateException(
                                    "Cloud TTS returned an unexpected response."
                            );
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> response =
                                (Map<String, Object>) rawData;

                        Object audioValue =
                                response.get("audioBase64");

                        if (!(audioValue instanceof String)
                                || ((String) audioValue).trim().isEmpty()) {

                            throw new IllegalStateException(
                                    "Cloud TTS returned no audio."
                            );
                        }

                        String audioBase64 =
                                (String) audioValue;

                        byte[] audioBytes = Base64.decode(
                                audioBase64,
                                Base64.DEFAULT
                        );

                        if (audioBytes.length == 0) {

                            throw new IllegalStateException(
                                    "Cloud TTS audio was empty."
                            );
                        }

                        playCloudSpeechAudio(
                                audioBytes,
                                text,
                                requestId
                        );

                    } catch (Exception e) {

                        Log.e(
                                TAG,
                                "Could not process Google Cloud TTS audio.",
                                e
                        );

                        fallbackToAndroidTts(
                                text,
                                requestId
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    if (requestId != cloudTtsRequestId) {
                        return;
                    }

                    Log.w(
                            TAG,
                            "Google Cloud TTS request failed; "
                                    + "using Android TTS fallback.",
                            e
                    );

                    fallbackToAndroidTts(
                            text,
                            requestId
                    );
                });
    }

    // ---------------------------------------------------------
    // PLAY GOOGLE CLOUD MP3 AUDIO
    // ---------------------------------------------------------

    private void playCloudSpeechAudio(
            byte[] audioBytes,
            String originalText,
            int requestId
    ) {

        if (!isAdded()
                || binding == null
                || requestId != cloudTtsRequestId) {

            return;
        }

        try {

            stopCloudTtsAudioOnly();

            File outputFile = File.createTempFile(
                    "speaksmart_cloud_tts_",
                    ".mp3",
                    requireContext().getCacheDir()
            );

            cloudTtsFile = outputFile;

            try (FileOutputStream outputStream =
                         new FileOutputStream(outputFile)) {

                outputStream.write(audioBytes);
                outputStream.flush();
            }

            MediaPlayer player = new MediaPlayer();

            cloudTtsPlayer = player;

            player.setOnPreparedListener(mp -> {

                if (cloudTtsPlayer != mp
                        || !isAdded()
                        || binding == null
                        || requestId != cloudTtsRequestId) {

                    if (cloudTtsPlayer == mp) {
                        cloudTtsPlayer = null;
                    }

                    releaseCloudPlayerAndFile(mp);

                    return;
                }

                try {

                    mp.start();

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "Could not start cloud-generated speech.",
                            e
                    );

                    if (cloudTtsPlayer == mp) {
                        cloudTtsPlayer = null;
                    }

                    releaseCloudPlayerAndFile(mp);

                    fallbackToAndroidTts(
                            originalText,
                            requestId
                    );
                }
            });

            player.setOnCompletionListener(mp -> {

                if (cloudTtsPlayer == mp) {
                    cloudTtsPlayer = null;
                }

                releaseCloudPlayerAndFile(mp);
            });

            player.setOnErrorListener((mp, what, extra) -> {

                Log.e(
                        TAG,
                        "Cloud audio playback error. what="
                                + what + ", extra=" + extra
                );

                if (cloudTtsPlayer == mp) {
                    cloudTtsPlayer = null;
                }

                releaseCloudPlayerAndFile(mp);

                fallbackToAndroidTts(
                        originalText,
                        requestId
                );

                return true;
            });

            player.setDataSource(outputFile.getAbsolutePath());
            player.prepareAsync();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not prepare cloud TTS audio.",
                    e
            );

            stopCloudTtsAudioOnly();

            fallbackToAndroidTts(
                    originalText,
                    requestId
            );
        }
    }

    // ---------------------------------------------------------
    // ANDROID TTS FALLBACK
    // ---------------------------------------------------------

    private void fallbackToAndroidTts(
            String text,
            int requestId
    ) {

        if (!isAdded()
                || binding == null
                || requestId != cloudTtsRequestId) {

            return;
        }

        stopCloudTtsAudioOnly();

        Log.i(
                TAG,
                "Using Android TTS fallback for mode: "
                        + translationMode
        );

        speakWithAndroidTts(text);
    }

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

                    Toast.makeText(
                            requireContext(),
                            "Speech could not be played. "
                                    + "Check your installed TTS voice.",
                            Toast.LENGTH_LONG
                    ).show();
                }

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Android TTS fallback failed.",
                        e
                );

                Toast.makeText(
                        requireContext(),
                        "Speech is unavailable on this device.",
                        Toast.LENGTH_LONG
                ).show();
            }

        } else {

            Toast.makeText(
                    requireContext(),
                    "Speech is not ready yet. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ---------------------------------------------------------
    // CLOUD AUDIO CLEANUP
    // ---------------------------------------------------------

    private void cancelCloudTtsSpeech() {

        cloudTtsRequestId++;

        stopCloudTtsAudioOnly();
    }

    private void stopCloudTtsAudioOnly() {

        MediaPlayer player = cloudTtsPlayer;

        cloudTtsPlayer = null;

        if (player != null) {

            try {
                player.setOnPreparedListener(null);
                player.setOnCompletionListener(null);
                player.setOnErrorListener(null);
            } catch (Exception ignored) {
            }

            try {
                player.stop();
            } catch (Exception ignored) {
            }

            try {
                player.release();
            } catch (Exception ignored) {
            }
        }

        File file = cloudTtsFile;

        cloudTtsFile = null;

        deleteTemporaryAudioFile(file);
    }

    private void releaseCloudPlayerAndFile(
            MediaPlayer player
    ) {

        if (player != null) {

            try {
                player.setOnPreparedListener(null);
                player.setOnCompletionListener(null);
                player.setOnErrorListener(null);
            } catch (Exception ignored) {
            }

            try {
                player.release();
            } catch (Exception ignored) {
            }
        }

        File file = cloudTtsFile;

        cloudTtsFile = null;

        deleteTemporaryAudioFile(file);
    }

    private void deleteTemporaryAudioFile(
            @Nullable File file
    ) {

        if (file != null && file.exists()) {

            try {

                if (!file.delete()) {

                    Log.w(
                            TAG,
                            "Could not delete temporary speech file."
                    );
                }

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Error deleting temporary speech file.",
                        e
                );
            }
        }
    }

    // ---------------------------------------------------------
    // RECORDED ILOKANO AUDIO
    // ---------------------------------------------------------

    private void playIlokanoRecordings() {

        stopIlokanoAudio();

        if (currentIlokanoAudioUrls.isEmpty()) {

            Toast.makeText(
                    requireContext(),
                    "Recorded Ilokano pronunciation is not available "
                            + "for this translation yet.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        currentAudioIndex = 0;
        audioPlaybackHadError = false;

        playNextIlokanoRecording();
    }

    private void playNextIlokanoRecording() {

        if (!isAdded()) {

            stopIlokanoAudio();

            return;
        }

        if (currentAudioIndex >= currentIlokanoAudioUrls.size()) {

            currentAudioIndex = 0;

            if (audioPlaybackHadError && getContext() != null) {

                Toast.makeText(
                        requireContext(),
                        "Some pronunciation recordings could not be played.",
                        Toast.LENGTH_LONG
                ).show();
            }

            audioPlaybackHadError = false;

            return;
        }

        String audioUrl =
                currentIlokanoAudioUrls.get(currentAudioIndex);

        if (audioUrl == null || audioUrl.trim().isEmpty()) {

            currentAudioIndex++;

            playNextIlokanoRecording();

            return;
        }

        MediaPlayer player = new MediaPlayer();

        pronunciationPlayer = player;

        player.setOnPreparedListener(mp -> {

            if (pronunciationPlayer != mp || !isAdded()) {

                releasePlayer(mp);

                return;
            }

            try {

                mp.start();

            } catch (IllegalStateException e) {

                audioPlaybackHadError = true;

                if (pronunciationPlayer == mp) {
                    pronunciationPlayer = null;
                }

                releasePlayer(mp);

                currentAudioIndex++;

                playNextIlokanoRecording();
            }
        });

        player.setOnCompletionListener(mp -> {

            if (pronunciationPlayer == mp) {
                pronunciationPlayer = null;
            }

            releasePlayer(mp);

            currentAudioIndex++;

            playNextIlokanoRecording();
        });

        player.setOnErrorListener((mp, what, extra) -> {

            if (pronunciationPlayer == mp) {
                pronunciationPlayer = null;
            }

            releasePlayer(mp);

            audioPlaybackHadError = true;

            currentAudioIndex++;

            playNextIlokanoRecording();

            return true;
        });

        try {

            player.setDataSource(audioUrl.trim());

            player.prepareAsync();

        } catch (Exception e) {

            Log.e(
                    "TranslatorAudio",
                    "Could not load Ilokano pronunciation: " + audioUrl,
                    e
            );

            if (pronunciationPlayer == player) {
                pronunciationPlayer = null;
            }

            releasePlayer(player);

            audioPlaybackHadError = true;

            currentAudioIndex++;

            playNextIlokanoRecording();
        }
    }

    private void releasePlayer(MediaPlayer player) {

        if (player == null) {
            return;
        }

        try {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
        } catch (Exception ignored) {
        }

        try {
            player.release();
        } catch (Exception ignored) {
        }
    }

    private void stopIlokanoAudio() {

        MediaPlayer player = pronunciationPlayer;

        pronunciationPlayer = null;
        currentAudioIndex = 0;

        if (player == null) {
            return;
        }

        try {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
        } catch (Exception ignored) {
        }

        try {
            player.stop();
        } catch (IllegalStateException ignored) {
        }

        try {
            player.release();
        } catch (Exception ignored) {
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

    @Nullable
    private String getValidAudioUrl(@Nullable Word word) {

        if (word == null) {
            return null;
        }

        String audioUrl = word.getTtsUrl();

        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            return null;
        }

        return audioUrl.trim();
    }

    @Nullable
    private String getValidPhraseAudioUrl(
            @Nullable Phrase phrase
    ) {

        if (phrase == null) {
            return null;
        }

        String audioUrl = phrase.getTtsUrl();

        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            return null;
        }

        return audioUrl.trim();
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

        if (output == null || output.trim().isEmpty()) {
            return false;
        }

        if (isIlokanoOutputMode()) {

            String audioUrl =
                    getValidPhraseAudioUrl(phrase);

            return audioUrl != null
                    && !audioUrl.trim().isEmpty();
        }

        return true;
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

        List<String> audioUrls = new ArrayList<>();

        if (isIlokanoOutputMode()) {

            String audioUrl =
                    getValidPhraseAudioUrl(phrase);

            if (audioUrl != null) {
                audioUrls.add(audioUrl);
            }
        }

        showTranslation(
                capitalizeSentence(output),
                audioUrls
        );
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

        final String[] audioUrls =
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
                        audioUrls,
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

                                    if (isIlokanoOutputMode()) {

                                        audioUrls[wordIndex] =
                                                getValidAudioUrl(item);
                                    }
                                }
                            }

                            translatedParts[wordIndex] = translated;

                            completeOnlineWord(
                                    input,
                                    translatedParts,
                                    audioUrls,
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
                                    audioUrls,
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
            String[] audioUrls,
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

        List<String> recordedAudio =
                new ArrayList<>();

        for (int i = 0; i < totalWords; i++) {

            String translatedPart = translatedParts[i];

            if (translatedPart != null) {

                translatedSentence
                        .append(translatedPart)
                        .append(" ");
            }

            if (isIlokanoOutputMode()
                    && audioUrls[i] != null
                    && !audioUrls[i].trim().isEmpty()) {

                recordedAudio.add(audioUrls[i].trim());
            }
        }

        showTranslation(
                capitalizeSentence(
                        translatedSentence.toString().trim()
                ),
                recordedAudio
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

            String[] audioUrls =
                    new String[words.length];

            for (int i = 0; i < words.length; i++) {

                String originalWord = words[i];

                String clean = originalWord
                        .replaceAll("[^\\p{L}\\p{N}]", "")
                        .toLowerCase(Locale.ROOT);

                String output = originalWord;

                Word matchedWord = null;

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

                            matchedWord = item;

                            break;
                        }
                    }
                }

                translatedParts[i] = output;

                if (isIlokanoOutputMode()
                        && matchedWord != null) {

                    audioUrls[i] =
                            getValidAudioUrl(matchedWord);
                }
            }

            StringBuilder translatedSentence =
                    new StringBuilder();

            List<String> recordedAudio =
                    new ArrayList<>();

            for (int i = 0; i < translatedParts.length; i++) {

                if (translatedParts[i] != null) {

                    translatedSentence
                            .append(translatedParts[i])
                            .append(" ");
                }

                if (isIlokanoOutputMode()
                        && audioUrls[i] != null
                        && !audioUrls[i].trim().isEmpty()) {

                    recordedAudio.add(audioUrls[i].trim());
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

                        showTranslation(
                                finalTranslation,
                                recordedAudio
                        );
                    }
            );
        });
    }

    // ---------------------------------------------------------
    // DISPLAY TRANSLATION AND STORE AUDIO URLS
    // ---------------------------------------------------------

    private void showTranslation(
            String translation,
            @Nullable List<String> audioUrls
    ) {

        if (binding == null || !isAdded()) {
            return;
        }

        binding.translatedText.setText(translation);

        currentIlokanoAudioUrls.clear();

        if (isIlokanoOutputMode() && audioUrls != null) {

            for (String audioUrl : audioUrls) {

                if (audioUrl != null
                        && !audioUrl.trim().isEmpty()) {

                    currentIlokanoAudioUrls.add(
                            audioUrl.trim()
                    );
                }
            }
        }
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    public void onDestroyView() {

        searchHandler.removeCallbacksAndMessages(null);

        cancelCloudTtsSpeech();

        stopIlokanoAudio();

        currentIlokanoAudioUrls.clear();

        if (tts != null) {

            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }

            tts = null;
        }

        firebaseFunctions = null;

        binding = null;

        super.onDestroyView();
    }
}