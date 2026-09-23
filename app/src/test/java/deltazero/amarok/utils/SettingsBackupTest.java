package deltazero.amarok.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SettingsBackupTest {

    private static Map<String, Object> sample() {
        var prefs = new HashMap<String, Object>();
        prefs.put("hidePkgNames", Set.of("com.example.chat", "com.example.game"));
        prefs.put("hideFilePath", Set.of("/storage/emulated/0/Private"));
        prefs.put("appHiderMode", 3);
        prefs.put("enableAutoHide", true);
        prefs.put("disguiseType", "calculator");
        prefs.put("someLong", 1_700_000_000_000L);
        prefs.put("someFloat", 0.1f);
        return prefs;
    }

    @Test
    public void roundTripKeepsEveryValueAndType() throws JSONException {
        Map<String, Object> restored = SettingsBackup.parse(SettingsBackup.export(sample()));

        assertEquals(sample(), restored);
        assertEquals(Integer.class, restored.get("appHiderMode").getClass());
        assertEquals(Long.class, restored.get("someLong").getClass());
        assertEquals(Float.class, restored.get("someFloat").getClass());
    }

    @Test
    public void leavesOutThePasswordAndTheCurrentState() throws JSONException {
        var prefs = sample();
        prefs.put("amarokPassword", "hash");
        prefs.put("isHidden", true);

        String exported = SettingsBackup.export(prefs);

        assertFalse(exported.contains("amarokPassword"));
        assertFalse(exported.contains("isHidden"));
        assertEquals(sample(), SettingsBackup.parse(exported));
    }

    @Test
    public void ignoresExcludedKeysInAHandEditedFile() throws JSONException {
        String file = "{\"app\":\"amarok\",\"version\":1,\"prefs\":{"
                + "\"amarokPassword\":{\"type\":\"string\",\"value\":\"x\"},"
                + "\"appHiderMode\":{\"type\":\"int\",\"value\":1}}}";

        assertEquals(Map.of("appHiderMode", 1), SettingsBackup.parse(file));
    }

    @Test
    public void rejectsOtherFiles() {
        assertThrows(JSONException.class, () -> SettingsBackup.parse("not json"));
        assertThrows(JSONException.class, () -> SettingsBackup.parse("{\"app\":\"other\",\"version\":1,\"prefs\":{}}"));
        assertThrows(JSONException.class, () -> SettingsBackup.parse("{\"app\":\"amarok\",\"version\":99,\"prefs\":{}}"));
    }
}
