package ro.eterra.check;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ScreenshotReader {
    private static final int SLOT = 84;
    private static final int BATCH = 30;
    private static final int ATLAS_WIDTH = 1000;

    static List<CaseRow> read(Bitmap image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
        List<TableGrid.Cell[]> cells = TableGrid.locate(pixels, width, height);
        String[][] values = new String[cells.size()][4];
        TextRecognizer reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            for (int column = 0; column < 4; column++) {
                for (int start = 0; start < cells.size(); start += BATCH) {
                    readBatch(reader, image, cells, values, column, start);
                }
            }
        } finally {
            reader.close();
        }
        String[] expected = {"status", "client", "inregistrare", "cerere"};
        for (int i = 0; i < expected.length; i++) {
            if (!CaseRow.normalized(values[0][i]).contains(expected[i])) {
                throw new IllegalArgumentException("Antetul tabelului nu a fost citit complet. Include coloanele Status, Client, Cod înregistrare și Nr. cerere.");
            }
        }
        List<CaseRow> rows = new ArrayList<>();
        for (int i = 1; i < values.length; i++) {
            String[] row = values[i];
            if (Arrays.stream(row).allMatch(String::isEmpty)) continue;
            rows.add(new CaseRow(row[2].replaceAll("\\s", "").toUpperCase(Locale.ROOT),
                    row[3].replaceAll("\\s", ""), row[1], CaseRow.displayStatus(row[0])));
        }
        return rows;
    }

    private static void readBatch(TextRecognizer reader, Bitmap image, List<TableGrid.Cell[]> cells,
                                  String[][] values, int column, int start) throws Exception {
        int count = Math.min(BATCH, cells.size() - start);
        Bitmap atlas = Bitmap.createBitmap(ATLAS_WIDTH, count * SLOT, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(atlas);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            for (int i = 0; i < count; i++) {
                TableGrid.Cell cell = cells.get(start + i)[column];
                Rect src = new Rect(cell.left(), cell.top(), cell.right(), cell.bottom());
                int height = 58;
                int width = Math.min(ATLAS_WIDTH - 32, Math.round(src.width() * 58f / src.height()));
                int top = i * SLOT + 13;
                canvas.drawBitmap(image, src, new Rect(16, top, 16 + width, top + height), paint);
                values[start + i][column] = "";
            }
            Text result = Tasks.await(reader.process(InputImage.fromBitmap(atlas, 0)));
            List<Text.Line> lines = new ArrayList<>();
            for (Text.TextBlock block : result.getTextBlocks()) lines.addAll(block.getLines());
            lines.removeIf(line -> line.getBoundingBox() == null);
            lines.sort(Comparator.comparingInt(line -> line.getBoundingBox().left));
            for (Text.Line line : lines) {
                int slot = line.getBoundingBox().centerY() / SLOT;
                if (slot >= 0 && slot < count) {
                    values[start + slot][column] =
                            (values[start + slot][column] + " " + line.getText()).trim();
                }
            }
        } finally {
            atlas.recycle();
        }
    }
}
