package ro.eterra.check;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CaseBook {
    record Change(CaseRow row, String before, long time) {}
    record ImportResult(int added, int changed) {}

    private final SharedPreferences preferences;
    private boolean hasSavedSnapshot;
    private Map<String, CaseRow> cases = new LinkedHashMap<>();
    private Map<String, CaseRow> previousImport = new LinkedHashMap<>();
    private List<Change> history = new ArrayList<>();

    CaseBook(SharedPreferences preferences) throws JSONException {
        this.preferences = preferences;
        JSONObject data = new JSONObject(preferences.getString("records", "{}"));
        JSONArray rows = data.optJSONArray("cases");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            CaseRow row = decode(rows.getJSONObject(i));
            String key = CaseRow.validNumber(row.number()) && !cases.containsKey(row.number())
                    ? row.number() : "#" + i;
            cases.put(key, row);
        }
        JSONArray lastImport = data.optJSONArray("lastImport");
        hasSavedSnapshot = lastImport != null;
        if (!hasSavedSnapshot) {
            for (CaseRow row : cases.values()) {
                if (CaseRow.validNumber(row.number())) previousImport.putIfAbsent(row.number(), row);
            }
        } else {
            for (int i = 0; i < lastImport.length(); i++) {
                CaseRow row = decode(lastImport.getJSONObject(i));
                previousImport.put(row.number(), row);
            }
        }
        JSONArray changes = data.optJSONArray("history");
        for (int i = 0; changes != null && i < changes.length(); i++) {
            JSONObject entry = changes.getJSONObject(i);
            history.add(new Change(decode(entry), entry.getString("before"), entry.getLong("time")));
        }
    }

    List<CaseRow> cases() { return new ArrayList<>(cases.values()); }
    List<Change> history() { return new ArrayList<>(history); }

    boolean hasSavedSnapshot() { return hasSavedSnapshot; }

    List<CaseRow> previousImport() { return new ArrayList<>(previousImport.values()); }

    ImportResult apply(List<CaseRow> incoming, long now) throws JSONException {
        Map<String, CaseRow> nextCases = new LinkedHashMap<>(cases);
        Map<String, CaseRow> nextImport = new LinkedHashMap<>();
        List<Change> nextHistory = new ArrayList<>(history);
        int added = 0;
        int changed = 0;
        for (CaseRow row : incoming) {
            CaseRow previous = nextCases.get(row.number());
            if (previous == null) {
                added++;
            } else {
                row = new CaseRow(row.number(),
                        keepIfEmpty(row.client(), previous.client()), row.status(),
                        keepIfEmpty(row.uat(), previous.uat()), keepIfEmpty(row.deadline(), previous.deadline()),
                        keepIfEmpty(row.flow(), previous.flow()));
                if (!CaseRow.statusKey(previous.status()).equals(CaseRow.statusKey(row.status()))) {
                    nextHistory.add(0, new Change(row, previous.status(), now));
                    changed++;
                }
            }
            nextCases.put(row.number(), row);
            nextImport.put(row.number(), row);
        }

        JSONArray rows = new JSONArray();
        for (CaseRow row : nextCases.values()) rows.put(encode(row));
        JSONArray changes = new JSONArray();
        for (Change change : nextHistory) {
            changes.put(encode(change.row()).put("before", change.before()).put("time", change.time()));
        }
        JSONArray lastImport = new JSONArray();
        for (CaseRow row : nextImport.values()) lastImport.put(encode(row));
        String saved = new JSONObject().put("cases", rows).put("history", changes)
                .put("lastImport", lastImport).toString();
        if (!preferences.edit().putString("records", saved).commit()) {
            throw new IllegalStateException("Datele nu au putut fi scrise pe telefon.");
        }
        cases = nextCases;
        history = nextHistory;
        previousImport = nextImport;
        hasSavedSnapshot = true;
        return new ImportResult(added, changed);
    }

    private static String keepIfEmpty(String newValue, String oldValue) {
        return newValue.isEmpty() ? oldValue : newValue;
    }

    private static JSONObject encode(CaseRow row) throws JSONException {
        return new JSONObject().put("number", row.number())
                .put("client", row.client()).put("status", row.status())
                .put("uat", row.uat()).put("deadline", row.deadline()).put("flow", row.flow());
    }

    private static CaseRow decode(JSONObject row) throws JSONException {
        return new CaseRow(row.getString("number"),
                row.getString("client"), row.getString("status"),
                row.optString("uat"), row.optString("deadline"), row.optString("flow"));
    }
}
