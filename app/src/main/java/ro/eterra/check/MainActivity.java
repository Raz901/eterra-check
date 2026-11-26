package ro.eterra.check;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xffeef3f8;
    private static final int INK = 0xff102638;
    private static final int MUTED = 0xff4c6172;
    private static final int BLUE = 0xff00658b;
    private static final int WHITE = 0xffffffff;
    private static final int PICK_IMAGE = 10;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout body;
    private CaseBook book;
    private List<ReviewRow> review = new ArrayList<>();
    private TextView notice;
    private TextView preview;
    private boolean historyVisible;
    private boolean reading;
    private boolean selectingAll;
    private Uri pendingImage;
    private String message = "";

    private record ReviewRow(CheckBox include, EditText number, EditText client, EditText status,
                             EditText uat, EditText deadline, EditText flow) {}

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            book = new CaseBook(getSharedPreferences("eterra", MODE_PRIVATE));
        } catch (Exception failure) {
            setContentView(label("Istoricul salvat nu a putut fi deschis.", 18, INK, true));
            return;
        }
        renderHome();
        if (state != null) {
            Uri restored = state.getParcelable("pending");
            if (restored != null) load(restored);
        } else {
            receive(getIntent());
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("pending", pendingImage);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (pendingImage != null) {
            android.widget.Toast.makeText(this, "Finalizează importul curent înainte de a distribui altă captură.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        receive(intent);
    }

    @Override public void onBackPressed() {
        if (!review.isEmpty()) {
            pendingImage = null;
            message = "";
            renderHome();
        } else {
            super.onBackPressed();
        }
    }

    private void receive(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri image = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (image != null && "content".equals(image.getScheme())) load(image);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            load(data.getData());
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void load(Uri uri) {
        if (reading || book == null) return;
        pendingImage = uri;
        reading = true;
        message = "Se citește captura…";
        renderHome();
        worker.execute(() -> {
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int largest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (largest > 4200) {
                        float scale = 4200f / largest;
                        decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                                Math.max(1, Math.round(info.getSize().getHeight() * scale)));
                    }
                });
                List<CaseRow> rows;
                try {
                    rows = ScreenshotReader.read(bitmap);
                } finally {
                    bitmap.recycle();
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    reading = false;
                    if (rows.isEmpty()) {
                        pendingImage = null;
                        message = "Captura nu conține rânduri lizibile.";
                        renderHome();
                    } else {
                        renderReview(rows);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    reading = false;
                    pendingImage = null;
                    message = error instanceof IllegalArgumentException ? error.getMessage()
                            : "Captura nu a putut fi citită. Încearcă imaginea originală.";
                    renderHome();
                });
            }
        });
    }

    private void frame(String title) {
        root = column();
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = column();
        header.setBackgroundColor(INK);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.addView(label(title, 28, WHITE, true));
        root.addView(header);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = column();
        body.setPadding(dp(16), dp(16), dp(16), dp(20));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestApplyInsets();
    }

    private void renderHome() {
        review.clear();
        frame("eTerra Check · " + BuildConfig.VERSION_NAME);
        Button importButton = button("Importă captură", BLUE, WHITE, view -> pickImage());
        importButton.setEnabled(!reading);
        body.addView(importButton);
        if (!message.isEmpty()) addNotice(message);
        LinearLayout tabs = new LinearLayout(this);
        tabs.addView(button("Documentații (" + book.cases().size() + ")", historyVisible ? WHITE : INK,
                historyVisible ? INK : WHITE, view -> { historyVisible = false; renderHome(); }),
                new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(button("Schimbări (" + book.history().size() + ")", historyVisible ? INK : WHITE,
                historyVisible ? WHITE : INK, view -> { historyVisible = true; renderHome(); }),
                new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(tabs);
        if (historyVisible) {
            if (book.history().isEmpty()) addNotice("Schimbările apar după ce imporți o captură mai recentă.");
            for (CaseBook.Change change : book.history()) {
                LinearLayout card = caseCard(change.row());
                card.addView(label(change.before() + " → " + change.row().status(), 17,
                        statusColor(change.row().status()), true));
                card.addView(label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(change.time())), 13, MUTED, false));
            }
        } else {
            if (book.cases().isEmpty()) addNotice("Alege o captură eTerra cu antetul tabelului vizibil sau distribuie imaginea către eTerra Check.");
            for (CaseRow row : book.cases()) {
                caseCard(row).addView(label(row.status(), 16, statusColor(row.status()), true));
            }
        }
    }

    private LinearLayout caseCard(CaseRow row) {
        LinearLayout card = card();
        card.addView(label(row.client().isEmpty() ? "Beneficiar necompletat" : row.client(), 18, INK, true));
        String identity = row.number().isEmpty() ? "Nr. cerere necompletat" : "Cerere " + row.number();
        card.addView(label(identity, 14, MUTED, false));
        if (!row.uat().isEmpty()) card.addView(label("UAT · " + row.uat(), 14, MUTED, false));
        if (!row.deadline().isEmpty()) card.addView(label("Termen · " + row.deadline(), 14, MUTED, false));
        if (!row.flow().isEmpty()) card.addView(label("Flux · " + row.flow(), 14, MUTED, false));
        return card;
    }

    private void renderReview(List<CaseRow> rows) {
        frame("Verifică importul");
        preview = label("", 16, INK, false);
        card().addView(preview);
        addNotice(rows.size() + " rânduri citite. Corectează câmpurile și debifează rândurile pe care vrei să le omiți."
                + " Rândurile fără număr lizibil sunt debifate. Importă capturile în ordine cronologică.");
        for (CaseRow row : rows) {
            LinearLayout item = card();
            CheckBox include = new CheckBox(this);
            include.setText("Importă rândul");
            include.setChecked(CaseRow.validNumber(row.number()));
            item.addView(include);
            EditText number = field(item, "Nr. cerere", row.number(), true);
            EditText client = field(item, "Beneficiar", row.client(), false);
            EditText status = field(item, "Stare", row.status(), false);
            EditText uat = field(item, "UAT", row.uat(), false);
            EditText deadline = field(item, "Termen cerere", row.deadline(), false);
            EditText flow = field(item, "Flux", row.flow(), false);
            review.add(new ReviewRow(include, number, client, status, uat, deadline, flow));
            include.setOnCheckedChangeListener((button, checked) -> {
                if (!selectingAll) refreshPreview();
            });
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable text) { refreshPreview(); }
            };
            for (EditText input : new EditText[]{number, client, status, uat, deadline, flow}) {
                input.addTextChangedListener(watcher);
            }
        }
        refreshPreview();
        LinearLayout toolbar = column();
        toolbar.setPadding(dp(16), 0, dp(16), 0);
        toolbar.addView(button("Selectează toate", WHITE, INK, view -> selectAll()));
        root.addView(toolbar);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(16), dp(4), dp(16), dp(8));
        actions.addView(button("Renunță", WHITE, INK, view -> {
            pendingImage = null;
            message = "";
            renderHome();
        }), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Salvează importul", BLUE, WHITE, view -> save()),
                new LinearLayout.LayoutParams(0, -2, 2));
        root.addView(actions);
    }

    private void selectAll() {
        selectingAll = true;
        try {
            for (ReviewRow row : review) {
                row.include().setChecked(CaseRow.validNumber(value(row.number())));
            }
        } finally {
            selectingAll = false;
        }
        refreshPreview();
    }

    private void refreshPreview() {
        Map<String, CaseRow> previous = new LinkedHashMap<>();
        for (CaseRow row : book.previousImport()) previous.put(row.number(), row);
        if (previous.isEmpty()) {
            preview.setText("Prima captură salvată va servi la comparația următoare.");
            return;
        }

        Map<String, CaseRow> current = new LinkedHashMap<>();
        int unresolved = 0;
        for (ReviewRow field : review) {
            if (!field.include().isChecked()) {
                if (!CaseRow.validNumber(value(field.number()))) unresolved++;
                continue;
            }
            CaseRow row = reviewValue(field);
            if (!CaseRow.validNumber(row.number()) || row.status().isEmpty()
                    || current.putIfAbsent(row.number(), row) != null) unresolved++;
        }
        if (current.isEmpty()) {
            preview.setText("Selectează cel puțin o cerere cu număr și stare pentru comparație.");
            return;
        }
        Set<String> seen = new HashSet<>();
        List<String> details = new ArrayList<>();
        int added = 0;
        int matched = 0;
        for (CaseRow row : current.values()) {
            CaseRow before = previous.get(row.number());
            if (before == null) {
                added++;
                continue;
            }
            matched++;
            seen.add(row.number());
            List<String> changes = new ArrayList<>();
            if (!CaseRow.statusKey(before.status()).equals(CaseRow.statusKey(row.status()))) {
                changes.add(before.status() + " → " + row.status());
            }
            compareField(changes, "Beneficiar", before.client(), row.client());
            compareField(changes, "UAT", before.uat(), row.uat());
            compareField(changes, "Termen", before.deadline(), row.deadline());
            compareField(changes, "Flux", before.flow(), row.flow());
            if (!changes.isEmpty()) {
                String client = row.client().isEmpty() ? before.client() : row.client();
                details.add("Cerere " + row.number() + (client.isEmpty() ? "" : " · " + client)
                        + "\n" + String.join("\n", changes));
            }
        }

        String conclusion = matched == 0 ? "Nicio cerere comună pentru comparație."
                : details.isEmpty() ? "Fără modificări la cererile comune."
                : details.size() + " cereri cu modificări.";
        String baseline = book.hasSavedSnapshot() ? "Față de captura anterioară" : "Față de lucrările deja salvate";
        String summary = baseline + "\n" + conclusion + "\n"
                + added + " apar în această captură · " + (previous.size() - seen.size())
                + " lipsesc din selecție";
        if (unresolved > 0) summary += "\n" + unresolved + " rânduri rămân de verificat.";
        if (!details.isEmpty()) summary += "\n\n" + String.join("\n\n", details);
        preview.setText(summary);
    }

    private static void compareField(List<String> changes, String label, String before, String after) {
        if (!before.isEmpty() && !after.isEmpty()
                && !CaseRow.normalized(before).equals(CaseRow.normalized(after))) {
            changes.add(label + " · " + before + " → " + after);
        }
    }

    private static CaseRow reviewValue(ReviewRow row) {
        return new CaseRow(value(row.number()), value(row.client()), CaseRow.displayStatus(value(row.status())),
                value(row.uat()), value(row.deadline()), value(row.flow()));
    }

    private void save() {
        List<CaseRow> incoming = new ArrayList<>();
        Set<String> numbers = new HashSet<>();
        for (ReviewRow row : review) {
            if (!row.include().isChecked()) continue;
            String number = value(row.number());
            String status = value(row.status());
            if (!CaseRow.validNumber(number)) {
                fieldError(row.number(), "Introdu numărul cererii, folosind doar cifre.");
                return;
            }
            if (!numbers.add(number)) {
                fieldError(row.number(), "Acest număr de cerere apare de două ori în selecție.");
                return;
            }
            if (status.isEmpty()) {
                fieldError(row.status(), "Completează starea citită din captură.");
                return;
            }
            incoming.add(reviewValue(row));
        }
        if (incoming.isEmpty()) {
            notice.setText("Selectează cel puțin un rând pentru import.");
            notice.requestRectangleOnScreen(new android.graphics.Rect(0, 0, notice.getWidth(), notice.getHeight()));
            return;
        }
        try {
            CaseBook.ImportResult result = book.apply(incoming, System.currentTimeMillis());
            pendingImage = null;
            message = "Import salvat · " + incoming.size() + " procesate · " + result.added()
                    + " noi · " + result.changed() + " schimbări";
            historyVisible = result.changed() > 0;
            renderHome();
        } catch (Exception failure) {
            notice.setText("Importul nu a fost salvat. Încearcă din nou.");
            notice.requestRectangleOnScreen(new android.graphics.Rect(0, 0, notice.getWidth(), notice.getHeight()));
        }
    }

    private void fieldError(EditText field, String error) {
        field.requestFocus();
        field.setError(error);
    }

    private EditText field(LinearLayout card, String title, String value, boolean numeric) {
        card.addView(label(title, 13, MUTED, true));
        EditText input = new EditText(this);
        input.setTextColor(INK);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        if (numeric) {
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
            input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        }
        input.setText(value);
        input.setContentDescription(title);
        card.addView(input);
        return input;
    }

    private static String value(EditText input) { return input.getText().toString().trim(); }

    private int statusColor(String status) {
        return switch (CaseRow.statusKey(status)) {
            case "solutionata" -> 0xff146742;
            case "necesita completari" -> 0xff9a3a0b;
            default -> BLUE;
        };
    }

    private void addNotice(String text) {
        notice = label(text, 15, MUTED, false);
        notice.setPadding(dp(3), dp(12), dp(3), dp(12));
        body.addView(notice);
    }

    private LinearLayout card() {
        LinearLayout card = column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(WHITE);
        background.setCornerRadius(dp(14));
        card.setBackground(background);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        body.addView(card, params);
        return card;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String text, int background, int color, View.OnClickListener action) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(text);
        view.setTextColor(color);
        view.setBackgroundTintList(ColorStateList.valueOf(background));
        view.setOnClickListener(action);
        return view;
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
