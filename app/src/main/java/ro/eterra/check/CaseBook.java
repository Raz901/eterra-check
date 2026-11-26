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
    private Map<String, CaseRow> cases = new LinkedHashMap<>();
    private List<Change> history = new ArrayList<>();

    CaseBook(SharedPreferences preferences) throws JSONException {
        this.preferences = preferences;
        JSONObject data = new JSONObject(preferences.getString("records", "{}"));
        JSONArray rows = data.optJSONArray("cases");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            CaseRow row = decode(rows.getJSONObject(i));
            cases.put(row.code(), row);
        }
        JSONArray changes = data.optJSONArray("history");
        for (int i = 0; changes != null && i < changes.length(); i++) {
            JSONObject entry = changes.getJSONObject(i);
            history.add(new Change(decode(entry), entry.getString("before"), entry.getLong("time")));
        }
    }

    List<CaseRow> cases() { return new ArrayList<>(cases.values()); }
    List<Change> history() { return new ArrayList<>(history); }

    ImportResult apply(List<CaseRow> incoming, long now) throws JSONException {
        Map<String, CaseRow> nextCases = new LinkedHashMap<>(cases);
        List<Change> nextHistory = new ArrayList<>(history);
        int added = 0;
        int changed = 0;
        for (CaseRow row : incoming) {
            CaseRow previous = nextCases.get(row.code());
            if (previous == null) {
                added++;
            } else {
                row = new CaseRow(row.code(), row.number().isEmpty() ? previous.number() : row.number(),
                        row.client().isEmpty() ? previous.client() : row.client(), row.status());
                if (!CaseRow.statusKey(previous.status()).equals(CaseRow.statusKey(row.status()))) {
                    nextHistory.add(0, new Change(row, previous.status(), now));
                    changed++;
                }
            }
            nextCases.put(row.code(), row);
        }

        JSONArray rows = new JSONArray();
        for (CaseRow row : nextCases.values()) rows.put(encode(row));
        JSONArray changes = new JSONArray();
        for (Change change : nextHistory) {
            changes.put(encode(change.row()).put("before", change.before()).put("time", change.time()));
        }
        String saved = new JSONObject().put("cases", rows).put("history", changes).toString();
        if (!preferences.edit().putString("records", saved).commit()) {
            throw new IllegalStateException("Datele nu au putut fi scrise pe telefon.");
        }
        cases = nextCases;
        history = nextHistory;
        return new ImportResult(added, changed);
    }

    private static JSONObject encode(CaseRow row) throws JSONException {
        return new JSONObject().put("code", row.code()).put("number", row.number())
                .put("client", row.client()).put("status", row.status());
    }

    private static CaseRow decode(JSONObject row) throws JSONException {
        return new CaseRow(row.getString("code"), row.getString("number"),
                row.getString("client"), row.getString("status"));
    }
}
