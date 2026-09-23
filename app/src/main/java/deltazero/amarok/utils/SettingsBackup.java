package deltazero.amarok.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Turns Amarok's settings, including the hidden apps and folders, into a file and back. Each
 * value keeps its type, so it is restored exactly as SharedPreferences held it.
 * <p>
 * Uses only org.json, so it is covered by the JVM unit tests.
 */
public final class SettingsBackup {

    public static final int FORMAT_VERSION = 1;
    public static final String MIME_TYPE = "application/json";

    /**
     * Left out of a backup. The password stays on the device, and the rest describes what is
     * hidden right now, which would be wrong on whatever device or moment it is restored to.
     */
    public static final Set<String> EXCLUDED_KEYS = Set.of(
            "amarokPassword",
            "enableAmarokBiometricAuth",
            "isHidden",
            "showWelcome",
            "wereDisabledPkgNames",
            "iconHiddenComponents"
    );

    private static final String KEY_APP = "app";
    private static final String KEY_VERSION = "version";
    private static final String KEY_PREFS = "prefs";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";
    private static final String APP_ID = "amarok";

    private SettingsBackup() {
    }

    /**
     * @param prefs Everything SharedPreferences holds, as from {@code getAll()}.
     * @return The backup file's content.
     */
    public static String export(Map<String, ?> prefs) throws JSONException {
        var entries = new JSONObject();
        for (var e : prefs.entrySet()) {
            if (EXCLUDED_KEYS.contains(e.getKey()) || e.getValue() == null)
                continue;
            entries.put(e.getKey(), encode(e.getValue()));
        }
        return new JSONObject()
                .put(KEY_APP, APP_ID)
                .put(KEY_VERSION, FORMAT_VERSION)
                .put(KEY_PREFS, entries)
                .toString(2);
    }

    /**
     * @param content A backup file's content.
     * @return The settings it holds, typed as SharedPreferences stores them.
     * @throws JSONException If it is not an Amarok backup, or from a newer version.
     */
    public static Map<String, Object> parse(String content) throws JSONException {
        var root = new JSONObject(content);
        if (!APP_ID.equals(root.optString(KEY_APP)))
            throw new JSONException("Not an Amarok backup");
        if (root.getInt(KEY_VERSION) > FORMAT_VERSION)
            throw new JSONException("Backup from a newer version: " + root.getInt(KEY_VERSION));

        var entries = root.getJSONObject(KEY_PREFS);
        var result = new HashMap<String, Object>();
        for (Iterator<String> it = entries.keys(); it.hasNext(); ) {
            String key = it.next();
            if (EXCLUDED_KEYS.contains(key))
                continue;
            result.put(key, decode(entries.getJSONObject(key)));
        }
        return result;
    }

    private static JSONObject encode(Object value) throws JSONException {
        var entry = new JSONObject();
        if (value instanceof Boolean) {
            entry.put(KEY_TYPE, "boolean").put(KEY_VALUE, value);
        } else if (value instanceof Integer) {
            entry.put(KEY_TYPE, "int").put(KEY_VALUE, value);
        } else if (value instanceof Long) {
            entry.put(KEY_TYPE, "long").put(KEY_VALUE, value);
        } else if (value instanceof Float) {
            // As a string, since JSON numbers would come back as doubles that do not round-trip.
            entry.put(KEY_TYPE, "float").put(KEY_VALUE, value.toString());
        } else if (value instanceof String) {
            entry.put(KEY_TYPE, "string").put(KEY_VALUE, value);
        } else if (value instanceof Set<?> set) {
            var array = new JSONArray();
            for (Object item : set)
                array.put(String.valueOf(item));
            entry.put(KEY_TYPE, "stringSet").put(KEY_VALUE, array);
        } else {
            throw new JSONException("Unsupported type: " + value.getClass());
        }
        return entry;
    }

    private static Object decode(JSONObject entry) throws JSONException {
        String type = entry.getString(KEY_TYPE);
        return switch (type) {
            case "boolean" -> entry.getBoolean(KEY_VALUE);
            case "int" -> entry.getInt(KEY_VALUE);
            case "long" -> entry.getLong(KEY_VALUE);
            case "float" -> Float.parseFloat(entry.getString(KEY_VALUE));
            case "string" -> entry.getString(KEY_VALUE);
            case "stringSet" -> {
                var array = entry.getJSONArray(KEY_VALUE);
                var set = new HashSet<String>();
                for (int i = 0; i < array.length(); i++)
                    set.add(array.getString(i));
                yield set;
            }
            default -> throw new JSONException("Unknown type: " + type);
        };
    }
}
