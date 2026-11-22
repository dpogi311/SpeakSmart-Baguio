package com.example.speaksmartbaguio.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores AI assistant conversations on the device so previous chats stack up
 * in a history list until the user chooses to delete them.
 */
public final class ChatHistoryStore {

    private static final String PREFS = "ai_chat_history";
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final int MAX_CONVERSATIONS = 50;

    public static class Conversation {
        public final String id;
        public final String title;
        public final long updatedAt;
        public final JSONArray messages;

        public Conversation(String id, String title, long updatedAt, JSONArray messages) {
            this.id = id;
            this.title = title;
            this.updatedAt = updatedAt;
            this.messages = messages;
        }

        public int messageCount() {
            return messages != null ? messages.length() : 0;
        }
    }

    private ChatHistoryStore() {
    }

    @NonNull
    public static List<Conversation> loadAll(Context context) {
        List<Conversation> result = new ArrayList<>();
        String raw = prefs(context).getString(KEY_CONVERSATIONS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                result.add(new Conversation(
                        obj.optString("id"),
                        obj.optString("title", "Chat"),
                        obj.optLong("updatedAt", 0L),
                        obj.optJSONArray("messages") != null ? obj.optJSONArray("messages") : new JSONArray()
                ));
            }
        } catch (Exception ignored) {
            // Corrupted data: start with an empty history.
        }
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    @Nullable
    public static Conversation get(Context context, String id) {
        for (Conversation c : loadAll(context)) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    public static void save(Context context, Conversation conversation) {
        List<Conversation> all = loadAll(context);
        List<Conversation> updated = new ArrayList<>();
        updated.add(conversation);
        for (Conversation c : all) {
            if (!c.id.equals(conversation.id)) {
                updated.add(c);
            }
        }
        if (updated.size() > MAX_CONVERSATIONS) {
            updated = new ArrayList<>(updated.subList(0, MAX_CONVERSATIONS));
        }
        write(context, updated);
    }

    public static void delete(Context context, String id) {
        List<Conversation> remaining = new ArrayList<>();
        for (Conversation c : loadAll(context)) {
            if (!c.id.equals(id)) remaining.add(c);
        }
        write(context, remaining);
    }

    public static void deleteAll(Context context) {
        prefs(context).edit().remove(KEY_CONVERSATIONS).apply();
    }

    private static void write(Context context, List<Conversation> list) {
        JSONArray array = new JSONArray();
        try {
            for (Conversation c : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", c.id);
                obj.put("title", c.title);
                obj.put("updatedAt", c.updatedAt);
                obj.put("messages", c.messages);
                array.put(obj);
            }
        } catch (Exception ignored) {
            return;
        }
        prefs(context).edit().putString(KEY_CONVERSATIONS, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
