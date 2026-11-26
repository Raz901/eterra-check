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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ScreenshotReader {
    private static final int SLOT = 84;
    private static final int BATCH = 14;
    private static final int MAX_CELL_WIDTH = 820;

    static List<CaseRow> read(Bitmap image) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
        List<TableGrid.Cell[]> grid = TableGrid.locate(pixels, width, height);
        List<TableGrid.Cell[]> cells = grid.subList(1, grid.size());
        String[][] values = new String[cells.size()][4];
        TextRecognizer reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            checkHeader(reader, image, grid.get(0));
            for (int column = 0; column < 4; column++) {
                for (int start = 0; start < cells.size(); start += BATCH) {
                    int end = Math.min(start + BATCH, cells.size());
                    List<TableGrid.Cell> strip = new ArrayList<>(end - start);
                    for (int row = start; row < end; row++) strip.add(cells.get(row)[column]);
                    List<String> result = readCells(reader, image, strip);
                    for (int row = start; row < end; row++) values[row][column] = result.get(row - start);
                }
            }
        } finally {
            reader.close();
        }
        List<CaseRow> rows = new ArrayList<>();
        for (String[] row : values) {
            if (Arrays.stream(row).allMatch(String::isEmpty)) continue;
            rows.add(new CaseRow(row[2].replaceAll("\\s", "").toUpperCase(Locale.ROOT),
                    row[3].replaceAll("\\s", ""), row[1], CaseRow.displayStatus(row[0])));
        }
        return rows;
    }

    private static void checkHeader(TextRecognizer reader, Bitmap image,
                                    TableGrid.Cell[] header) throws Exception {
        List<String> titles = readCells(reader, image, Arrays.asList(header));
        String[] expected = {"status", "client", "cod", "cerere"};
        int matches = 0;
        for (int i = 0; i < expected.length; i++) {
            if (CaseRow.normalized(titles.get(i)).contains(expected[i])) matches++;
        }
        // O literă nerecunoscută în antet nu invalidează restul tabelului.
        if (matches < 3) {
            throw new IllegalArgumentException("Antetul tabelului nu a fost citit complet. Include coloanele Status, Client, Cod înregistrare și Nr. cerere.");
        }
    }

    private static List<String> readCells(TextRecognizer reader, Bitmap image,
                                          List<TableGrid.Cell> cells) throws Exception {
        int count = cells.size();
        int atlasWidth = 0;
        for (TableGrid.Cell cell : cells) atlasWidth = Math.max(atlasWidth, cellWidth(cell) + 32);
        Bitmap atlas = Bitmap.createBitmap(atlasWidth, count * SLOT, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(atlas);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            for (int i = 0; i < count; i++) {
                TableGrid.Cell cell = cells.get(i);
                Rect src = new Rect(cell.left(), cell.top(), cell.right(), cell.bottom());
                int top = i * SLOT + 13;
                canvas.drawBitmap(image, src, new Rect(16, top, 16 + cellWidth(cell), top + 58), paint);
            }
            Text result = Tasks.await(reader.process(InputImage.fromBitmap(atlas, 0)));
            List<Text.Line> lines = new ArrayList<>();
            for (Text.TextBlock block : result.getTextBlocks()) lines.addAll(block.getLines());
            lines.removeIf(line -> line.getBoundingBox() == null);
            lines.sort(Comparator.comparingInt(line -> line.getBoundingBox().left));
            List<String> values = new ArrayList<>(Collections.nCopies(count, ""));
            for (Text.Line line : lines) {
                int slot = line.getBoundingBox().centerY() / SLOT;
                if (slot >= 0 && slot < count) {
                    values.set(slot, (values.get(slot) + " " + line.getText()).trim());
                }
            }
            return values;
        } finally {
            atlas.recycle();
        }
    }

    private static int cellWidth(TableGrid.Cell cell) {
        return Math.min(MAX_CELL_WIDTH, Math.round(
                (cell.right() - cell.left()) * 58f / (cell.bottom() - cell.top())));
    }
}
