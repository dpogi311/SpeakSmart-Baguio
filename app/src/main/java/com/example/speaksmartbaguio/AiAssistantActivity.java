package com.example.speaksmartbaguio;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.speaksmartbaguio.utils.AccessibilityPreferences;
import com.example.speaksmartbaguio.utils.AudioPreferences;
import com.example.speaksmartbaguio.utils.ChatHistoryStore;
import com.example.speaksmartbaguio.utils.IlokanoAiEngine;
import com.example.speaksmartbaguio.utils.IlokanoTtsHelper;
import com.example.speaksmartbaguio.utils.ThemeManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AiAssistantActivity extends AppCompatActivity {

    private static class ChatMessage {
        final boolean isUser;
        final String text;
        @Nullable
        final IlokanoAiEngine.EntryCard card;
        final boolean isTyping;

        ChatMessage(boolean isUser, String text, @Nullable IlokanoAiEngine.EntryCard card, boolean isTyping) {
            this.isUser = isUser;
            this.text = text != null ? text : "";
            this.card = card;
            this.isTyping = isTyping;
        }
    }

    private RecyclerView rvChatMessages;
    private EditText etAiInput;
    private ImageButton btnAiSend;
    private ImageButton btnAiMic;
    private ImageButton btnAiBack;
    private ImageButton btnAiHistory;
    private TextView btnAiClear;
    private TextView tvAiStatus;
    private LinearLayout quickPromptsContainer;

    private String currentConversationId = UUID.randomUUID().toString();

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private IlokanoAiEngine aiEngine;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean activityDestroyed = false;

    private final ActivityResultLauncher<Intent> speechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> spoken = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (spoken != null && !spoken.isEmpty() && spoken.get(0) != null) {
                        String spokenText = spoken.get(0).trim();
                        if (!spokenText.isEmpty()) {
                            sendMessage(spokenText);
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        rvChatMessages = findViewById(R.id.rvChatMessages);
        etAiInput = findViewById(R.id.etAiInput);
        btnAiSend = findViewById(R.id.btnAiSend);
        btnAiMic = findViewById(R.id.btnAiMic);
        btnAiBack = findViewById(R.id.btnAiBack);
        btnAiHistory = findViewById(R.id.btnAiHistory);
        btnAiClear = findViewById(R.id.btnAiClear);
        tvAiStatus = findViewById(R.id.tvAiStatus);
        quickPromptsContainer = findViewById(R.id.quickPromptsContainer);

        aiEngine = new IlokanoAiEngine(this);
        initTextToSpeech();

        adapter = new ChatAdapter(messages, this::playCardAudio);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(adapter);

        setupQuickPrompts();
        addWelcomeMessage();

        btnAiBack.setOnClickListener(v -> finish());
        btnAiHistory.setOnClickListener(v -> showHistorySheet());
        btnAiClear.setOnClickListener(v -> startNewChat());
        btnAiSend.setOnClickListener(v -> sendCurrentInput());
        btnAiMic.setOnClickListener(v -> startVoiceInput());

        etAiInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput();
                return true;
            }
            if (event != null
                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    sendCurrentInput();
                }
                return true;
            }
            return false;
        });

        etAiInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event != null && event.getAction() == KeyEvent.ACTION_UP) {
                    sendCurrentInput();
                }
                return true;
            }
            return false;
        });

        AccessibilityPreferences.applyToViewTree(this, getWindow().getDecorView());
    }

    private void setupQuickPrompts() {
        String[] prompts = new String[]{
                "How to say thank you?",
                "Greetings",
                "Travel phrases",
                "Food & Dining",
                "Baguio tips",
                "Quiz me!"
        };

        int padH = dpToPx(12);
        int padV = dpToPx(7);
        int marginEnd = dpToPx(8);

        for (String prompt : prompts) {
            TextView chip = new TextView(this);
            chip.setText(prompt);
            chip.setTextSize(12f);
            chip.setTextColor(ContextCompat.getColor(this, R.color.cyan_secondary));
            chip.setTypeface(ResourcesCompat.getFont(this, R.font.font_label), android.graphics.Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.dropdown_glass_background);
            chip.setPadding(padH, padV, padH, padV);
            chip.setClickable(true);
            chip.setFocusable(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(marginEnd);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> sendMessage(prompt));
            quickPromptsContainer.addView(chip);
        }
    }

    private void addWelcomeMessage() {
        messages.clear();
        messages.add(new ChatMessage(
                false,
                "Naimbag nga aldaw! I'm SpeakSmart AI, Your Companion in Learning Ilokano and Baguio Languages.\n\n"
                        + "Ask me how to say a word or phrase, browse topics like Travel or Food, or tap or type \"Quiz me!\" to practice.",
                null,
                false
        ));
        adapter.notifyDataSetChanged();
    }

    private void resetConversation() {
        aiEngine.resetSession();
        addWelcomeMessage();
        tvAiStatus.setText("Ilokano & Baguio Companion");
    }

    // ---------------------------------------------------------
    // CONVERSATION HISTORY (stacked until the user deletes them)
    // ---------------------------------------------------------

    private boolean hasUserMessages() {
        for (ChatMessage m : messages) {
            if (m.isUser) return true;
        }
        return false;
    }

    /** Saves the on-screen conversation so it stays in History. */
    private void persistCurrentConversation() {
        if (!hasUserMessages()) {
            return;
        }
        JSONArray array = new JSONArray();
        String title = "";
        try {
            for (ChatMessage m : messages) {
                if (m.isTyping) continue;
                JSONObject obj = new JSONObject();
                obj.put("u", m.isUser);
                obj.put("t", m.text);
                if (m.card != null) {
                    JSONObject c = new JSONObject();
                    c.put("il", m.card.ilokano);
                    c.put("en", m.card.english);
                    c.put("tl", m.card.tagalog);
                    c.put("pos", m.card.partOfSpeech);
                    c.put("cat", m.card.category);
                    c.put("ex", m.card.extraInfo);
                    c.put("tts", m.card.ttsUrl);
                    c.put("ph", m.card.isPhrase);
                    obj.put("card", c);
                }
                array.put(obj);
                if (title.isEmpty() && m.isUser) {
                    title = m.text.trim();
                }
            }
        } catch (Exception e) {
            return;
        }
        if (title.length() > 40) {
            title = title.substring(0, 40).trim() + "...";
        }
        ChatHistoryStore.save(this, new ChatHistoryStore.Conversation(
                currentConversationId,
                title.isEmpty() ? "Chat" : title,
                System.currentTimeMillis(),
                array
        ));
    }

    private void startNewChat() {
        boolean hadChat = hasUserMessages();
        persistCurrentConversation();
        beginFreshConversation();
        if (hadChat) {
            Toast.makeText(this, "Previous chat saved in History", Toast.LENGTH_SHORT).show();
        }
    }

    private void beginFreshConversation() {
        currentConversationId = UUID.randomUUID().toString();
        aiEngine.resetSession();
        addWelcomeMessage();
        tvAiStatus.setText("Ilokano & Baguio Companion");
    }

    private void loadConversation(ChatHistoryStore.Conversation conversation) {
        if (!conversation.id.equals(currentConversationId)) {
            persistCurrentConversation();
        }
        currentConversationId = conversation.id;
        aiEngine.resetSession();

        messages.clear();
        for (int i = 0; i < conversation.messages.length(); i++) {
            JSONObject obj = conversation.messages.optJSONObject(i);
            if (obj == null) continue;
            IlokanoAiEngine.EntryCard card = null;
            JSONObject c = obj.optJSONObject("card");
            if (c != null) {
                card = new IlokanoAiEngine.EntryCard(
                        c.optString("il"), c.optString("en"), c.optString("tl"),
                        c.optString("pos"), c.optString("cat"), c.optString("ex"),
                        c.optString("tts"), c.optBoolean("ph", false)
                );
            }
            messages.add(new ChatMessage(obj.optBoolean("u"), obj.optString("t"), card, false));
        }
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) {
            rvChatMessages.scrollToPosition(messages.size() - 1);
        }
        tvAiStatus.setText("Ilokano & Baguio Companion");
    }

    private void showHistorySheet() {
        persistCurrentConversation();

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_ai_history, null);
        dialog.setContentView(sheet);

        View internal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (internal != null) {
            internal.setBackgroundResource(android.R.color.transparent);
        }

        final LinearLayout listContainer = sheet.findViewById(R.id.historyListContainer);
        final TextView tvEmpty = sheet.findViewById(R.id.tvHistoryEmpty);
        final TextView btnDeleteAll = sheet.findViewById(R.id.btnHistoryDeleteAll);
        final Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {
            listContainer.removeAllViews();
            List<ChatHistoryStore.Conversation> all = ChatHistoryStore.loadAll(this);
            tvEmpty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
            btnDeleteAll.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);

            for (ChatHistoryStore.Conversation conv : all) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_chat_history, listContainer, false);
                TextView tvTitle = row.findViewById(R.id.tvHistoryTitle);
                TextView tvMeta = row.findViewById(R.id.tvHistoryMeta);
                ImageButton btnDelete = row.findViewById(R.id.btnHistoryDelete);

                tvTitle.setText(conv.title);
                CharSequence when = android.text.format.DateUtils.getRelativeTimeSpanString(
                        conv.updatedAt,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                );
                String current = conv.id.equals(currentConversationId) ? "  •  Current chat" : "";
                tvMeta.setText(when + "  •  " + conv.messageCount() + " messages" + current);

                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    loadConversation(conv);
                });

                btnDelete.setOnClickListener(v ->
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Delete this chat?")
                                .setMessage("\"" + conv.title + "\" will be permanently removed.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, which) -> {
                                    ChatHistoryStore.delete(this, conv.id);
                                    if (conv.id.equals(currentConversationId)) {
                                        beginFreshConversation();
                                    }
                                    refresh[0].run();
                                })
                                .show()
                );

                listContainer.addView(row);
            }
            AccessibilityPreferences.applyToViewTree(this, listContainer);
        };

        btnDeleteAll.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete all chats?")
                        .setMessage("All saved conversations will be permanently removed.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete all", (d, which) -> {
                            ChatHistoryStore.deleteAll(this);
                            beginFreshConversation();
                            refresh[0].run();
                        })
                        .show()
        );

        refresh[0].run();
        AccessibilityPreferences.applyToViewTree(this, sheet);
        dialog.show();
    }

    private void sendCurrentInput() {
        String text = etAiInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        etAiInput.setText("");
        sendMessage(text);
    }

    private void sendMessage(String text) {
        int userIndex = messages.size();
        messages.add(new ChatMessage(true, text, null, false));
        int typingIndex = messages.size();
        messages.add(new ChatMessage(false, "Thinking...", null, true));
        adapter.notifyItemRangeInserted(userIndex, 2);
        rvChatMessages.scrollToPosition(typingIndex);

        aiEngine.ask(text, reply -> {
            if (activityDestroyed || isFinishing()) {
                return;
            }
            if (typingIndex >= 0 && typingIndex < messages.size() && messages.get(typingIndex).isTyping) {
                messages.remove(typingIndex);
                adapter.notifyItemRemoved(typingIndex);
            }
            int replyIndex = messages.size();
            messages.add(new ChatMessage(false, reply.message, reply.card, false));
            adapter.notifyItemInserted(replyIndex);
            tvAiStatus.setText(reply.fromCloud ? "Cloud AI • Grounded" : "Smart Ilokano Assistant");
            rvChatMessages.scrollToPosition(replyIndex);
            rvChatMessages.post(this::persistCurrentConversation);
        });
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask SpeakSmart AI...");
        try {
            speechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input is not available on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------
    // AUDIO PRONUNCIATION (ANDROID TTS WITH ILOKANO NORMALIZATION)
    // ---------------------------------------------------------

    private void initTextToSpeech() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (activityDestroyed || isFinishing()) {
                if (tts != null) {
                    tts.shutdown();
                    tts = null;
                }
                return;
            }
            if (status == TextToSpeech.SUCCESS && tts != null) {
                ttsReady = true;
                IlokanoTtsHelper.configureForIlokano(tts);
                AudioPreferences.applyToTextToSpeech(this, tts);
            }
        });
    }

    private void playCardAudio(IlokanoAiEngine.EntryCard card) {
        if (card == null || card.ilokano.isEmpty()) {
            return;
        }
        speakWithTts(card.ilokano);
    }

    private void speakWithTts(String text) {
        if (!ttsReady || tts == null) {
            Toast.makeText(this, "Speech is still initializing...", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int res = IlokanoTtsHelper.speakIlokano(this, tts, text, "aiCardTts");
            if (res == TextToSpeech.ERROR) {
                Toast.makeText(this, "Unable to play audio.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to play audio.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    @Override
    protected void onPause() {
        persistCurrentConversation();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------
    // RECYCLERVIEW ADAPTER
    // ---------------------------------------------------------

    private interface OnSpeakClickListener {
        void onSpeak(IlokanoAiEngine.EntryCard card);
    }

    private static class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_USER = 1;
        private static final int TYPE_AI = 2;

        private final List<ChatMessage> items;
        private final OnSpeakClickListener speakListener;

        ChatAdapter(List<ChatMessage> items, OnSpeakClickListener speakListener) {
            this.items = items;
            this.speakListener = speakListener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).isUser ? TYPE_USER : TYPE_AI;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_USER) {
                return new UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
            }
            return new AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = items.get(position);
            if (holder instanceof UserViewHolder) {
                ((UserViewHolder) holder).tvUserMessage.setText(msg.text);
            } else if (holder instanceof AiViewHolder) {
                AiViewHolder ai = (AiViewHolder) holder;
                ai.tvAiMessage.setText(msg.text);

                if (msg.card != null && !msg.isTyping) {
                    ai.cardAiEntry.setVisibility(View.VISIBLE);
                    ai.tvCardIlokano.setText(msg.card.ilokano);

                    boolean hasPos = !msg.card.partOfSpeech.isEmpty();
                    boolean hasCat = !msg.card.category.isEmpty();
                    ai.tvCardPos.setVisibility(hasPos ? View.VISIBLE : View.GONE);
                    ai.tvCardPos.setText(msg.card.partOfSpeech);
                    ai.tvCardCategory.setVisibility(hasCat ? View.VISIBLE : View.GONE);
                    ai.tvCardCategory.setText(msg.card.category);
                    ai.layoutCardBadges.setVisibility((hasPos || hasCat) ? View.VISIBLE : View.GONE);

                    StringBuilder trans = new StringBuilder();
                    if (!msg.card.english.isEmpty()) {
                        trans.append("English: ").append(msg.card.english);
                    }
                    if (!msg.card.tagalog.isEmpty()) {
                        if (trans.length() > 0) trans.append("\n");
                        trans.append("Tagalog: ").append(msg.card.tagalog);
                    }
                    ai.tvCardTranslations.setText(trans.toString());

                    if (!msg.card.extraInfo.isEmpty()) {
                        ai.tvCardExtra.setVisibility(View.VISIBLE);
                        ai.tvCardExtra.setText((msg.card.isPhrase ? "Example: " : "Note: ") + msg.card.extraInfo);
                    } else {
                        ai.tvCardExtra.setVisibility(View.GONE);
                    }

                    ai.btnCardSpeak.setOnClickListener(v -> speakListener.onSpeak(msg.card));
                } else {
                    ai.cardAiEntry.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class UserViewHolder extends RecyclerView.ViewHolder {
            final TextView tvUserMessage;

            UserViewHolder(@NonNull View itemView) {
                super(itemView);
                tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
            }
        }

        static class AiViewHolder extends RecyclerView.ViewHolder {
            final TextView tvAiMessage;
            final View cardAiEntry;
            final TextView tvCardIlokano;
            final ImageButton btnCardSpeak;
            final View layoutCardBadges;
            final TextView tvCardPos;
            final TextView tvCardCategory;
            final TextView tvCardTranslations;
            final TextView tvCardExtra;

            AiViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAiMessage = itemView.findViewById(R.id.tvAiMessage);
                cardAiEntry = itemView.findViewById(R.id.cardAiEntry);
                tvCardIlokano = itemView.findViewById(R.id.tvCardIlokano);
                btnCardSpeak = itemView.findViewById(R.id.btnCardSpeak);
                layoutCardBadges = itemView.findViewById(R.id.layoutCardBadges);
                tvCardPos = itemView.findViewById(R.id.tvCardPos);
                tvCardCategory = itemView.findViewById(R.id.tvCardCategory);
                tvCardTranslations = itemView.findViewById(R.id.tvCardTranslations);
                tvCardExtra = itemView.findViewById(R.id.tvCardExtra);
            }
        }
    }
}
