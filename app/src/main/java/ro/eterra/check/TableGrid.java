package ro.eterra.check;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TableGrid {
    record Cell(int left, int top, int right, int bottom) {}

    // Ordinea coloanelor din eTerra. Anteturile sunt verificate prin OCR.
    private static final int[] COLUMNS = {0, 2, 7, 3, 9, 5};

    static List<Cell[]> locate(int[] pixels, int width, int height) {
        List<Cell[]> exact = locateByColor(pixels, width, height);
        List<Cell[]> contrast = locateByContrast(pixels, width, height);
        if (exact.size() > 1 && exact.size() >= contrast.size()) return exact;
        if (contrast.size() > 1) return contrast;
        throw invalid();
    }

    private static List<Cell[]> locateByColor(int[] pixels, int width, int height) {
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
        int firstBodyLine = findBodyStart(horizontal);
        if (firstBodyLine < 0) return Collections.emptyList();
        int bodyStart = horizontal.get(firstBodyLine);
        return buildRows(horizontal, firstBodyLine,
                lines(pixels, width, height, gridColor, true, bodyStart), width, true);
    }

    private static List<Cell[]> locateByContrast(int[] pixels, int width, int height) {
        byte[] luminance = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            luminance[i] = (byte) (((pixel >>> 16 & 255) * 3 + (pixel >>> 8 & 255) * 6
                    + (pixel & 255)) / 10);
        }
        List<Cell[]> result = Collections.emptyList();
        for (double coverage : new double[]{0.55, 0.45, 0.35}) {
            List<Cell[]> candidate = locateContrastGrid(luminance, width, height, coverage);
            if (candidate.size() > result.size()) result = candidate;
        }
        return result;
    }

    private static List<Cell[]> locateContrastGrid(byte[] luminance, int width, int height, double coverage) {
        List<Integer> horizontal = contrastLines(luminance, width, height, false, 0, width, coverage);
        int firstBodyLine = findBodyStart(horizontal);
        if (firstBodyLine < 0) return Collections.emptyList();
        int bodyStart = horizontal.get(firstBodyLine);
        int bodyEnd = horizontal.get(horizontal.size() - 1);
        List<Integer> vertical = contrastLines(luminance, width, height, true, bodyStart, bodyEnd, 0.55);
        return buildRows(horizontal, firstBodyLine, vertical, width, false);
    }

    private static List<Integer> contrastLines(byte[] values, int width, int height, boolean vertical,
                                                int from, int to, double coverage) {
        List<Integer> lines = new ArrayList<>();
        int length = vertical ? width : height;
        int step = vertical ? 1 : width;
        int best = 0;
        for (int i = 2; i + 2 < length; i++) {
            int count = 0;
            for (int j = from; j < to; j++) {
                int p = vertical ? j * width + i : i * width + j;
                int center = values[p] & 255;
                int before = values[p - 2 * step] & 255;
                int after = values[p + 2 * step] & 255;
                if (center >= 165 && before >= 205 && after >= 205
                        && center + 4 < Math.min(before, after)) count++;
            }
            if (count < (to - from) * coverage) continue;
            int last = lines.size() - 1;
            if (last < 0 || i - lines.get(last) > 2) {
                lines.add(i);
                best = count;
            } else if (count > best) {
                lines.set(last, i);
                best = count;
            }
        }
        return lines;
    }

    private static List<Cell[]> buildRows(List<Integer> horizontal, int firstBodyLine,
                                          List<Integer> vertical, int width, boolean exact) {
        if (vertical.size() < 9) return Collections.emptyList();
        int headerBottom = horizontal.get(0);
        int bodyStart = horizontal.get(firstBodyLine);
        int rowHeight = horizontal.get(firstBodyLine + 1) - bodyStart;
        int margin = exact ? 2 : Math.max(1, rowHeight / 12);
        int bottomMargin = exact ? 1 : 0;
        List<Cell[]> rows = new ArrayList<>();
        rows.add(cells(vertical, Math.max(0, headerBottom - Math.round(rowHeight * 0.86f)),
                headerBottom - bottomMargin, margin, bottomMargin, width));
        for (int i = firstBodyLine; i + 1 < horizontal.size(); i++) {
            int start = horizontal.get(i);
            int end = horizontal.get(i + 1);
            if (end - start >= rowHeight * 0.75 && end - start <= rowHeight * 1.35) {
                rows.add(cells(vertical, start + margin, end - bottomMargin, margin, bottomMargin, width));
            }
        }
        return rows;
    }

    private static int findBodyStart(List<Integer> lines) {
        if (lines.size() < 3) return -1;
        int headerBottom = lines.get(0);
        for (int i = 1; i + 1 < Math.min(lines.size(), 5); i++) {
            int rowHeight = lines.get(i + 1) - lines.get(i);
            int filterHeight = lines.get(i) - headerBottom;
            if (rowHeight >= 6 && filterHeight >= rowHeight * 1.15 && filterHeight <= rowHeight * 1.8) {
                return i;
            }
        }
        return -1;
    }

    private static Cell[] cells(List<Integer> x, int top, int bottom, int margin, int bottomMargin, int width) {
        Cell[] cells = new Cell[COLUMNS.length];
        for (int i = 0; i < COLUMNS.length; i++) {
            int column = COLUMNS[i];
            if (column >= x.size()) continue;
            int right = column + 1 < x.size() ? x.get(column + 1) : width;
            if (right > x.get(column) + margin + bottomMargin) {
                cells[i] = new Cell(x.get(column) + margin, top, right - bottomMargin, bottom);
            }
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
