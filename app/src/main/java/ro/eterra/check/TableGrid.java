package ro.eterra.check;

import java.util.ArrayList;
import java.util.List;

final class TableGrid {
    record Cell(int left, int top, int right, int bottom) {}

    // Ordinea coloanelor din eTerra. Anteturile sunt verificate prin OCR.
    private static final int[] COLUMNS = {0, 2, 6, 7};

    static List<Cell[]> locate(int[] pixels, int width, int height) {
        int[] histogram = new int[256];
        for (int pixel : pixels) {
            int gray = gray(pixel);
            if (gray >= 175 && gray <= 235) histogram[gray]++;
        }
        int gridColor = 175;
        for (int i = 176; i <= 235; i++) {
            if (histogram[i] > histogram[gridColor]) gridColor = i;
        }
        List<Integer> horizontal = lines(pixels, width, height, gridColor, false, 0);
        if (horizontal.size() < 3) throw invalid();
        int headerBottom = horizontal.get(0);
        int firstBodyLine = findBodyStart(horizontal, headerBottom);
        int bodyStart = horizontal.get(firstBodyLine);
        List<Integer> vertical = lines(pixels, width, height, gridColor, true, bodyStart);
        if (vertical.size() < 9) throw invalid();

        List<Cell[]> rows = new ArrayList<>();
        int rowHeight = horizontal.get(firstBodyLine + 1) - bodyStart;
        rows.add(cells(vertical, Math.max(0, headerBottom - rowHeight + 3), headerBottom - 1));
        for (int i = firstBodyLine; i + 1 < horizontal.size(); i++) {
            int start = horizontal.get(i);
            int end = horizontal.get(i + 1);
            if (end - start >= rowHeight * 0.75 && end - start <= rowHeight * 1.35) {
                rows.add(cells(vertical, start + 2, end - 1));
            }
        }
        return rows;
    }

    private static int findBodyStart(List<Integer> lines, int headerBottom) {
        for (int i = 1; i + 1 < Math.min(lines.size(), 5); i++) {
            int rowHeight = lines.get(i + 1) - lines.get(i);
            int filterHeight = lines.get(i) - headerBottom;
            if (rowHeight >= 10 && filterHeight >= rowHeight * 1.15 && filterHeight <= rowHeight * 1.8) {
                return i;
            }
        }
        throw invalid();
    }

    private static Cell[] cells(List<Integer> x, int top, int bottom) {
        Cell[] cells = new Cell[COLUMNS.length];
        for (int i = 0; i < COLUMNS.length; i++) {
            int column = COLUMNS[i];
            cells[i] = new Cell(x.get(column) + 2, top, x.get(column + 1) - 1, bottom);
        }
        return cells;
    }

    private static List<Integer> lines(int[] pixels, int width, int height, int color,
                                       boolean vertical, int bodyStart) {
        List<Integer> result = new ArrayList<>();
        int length = vertical ? width : height;
        int span = vertical ? height : width;
        for (int i = 0; i < length; i++) {
            int count = 0;
            for (int j = bodyStart; j < span; j++) {
                int gray = gray(pixels[vertical ? j * width + i : i * width + j]);
                if (Math.abs(gray - color) <= 5) count++;
            }
            if (count >= (span - bodyStart) * 0.60 &&
                    (result.isEmpty() || i - result.get(result.size() - 1) > 3)) {
                result.add(i);
            }
        }
        return result;
    }

    private static int gray(int pixel) {
        int r = (pixel >>> 16) & 255;
        int g = (pixel >>> 8) & 255;
        int b = pixel & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 5
                ? (r + g + b) / 3 : -100;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Nu am recunoscut tabelul eTerra. Alege o captură clară, cu antetul și coloanele complete.");
    }
}
