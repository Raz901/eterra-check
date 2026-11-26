package ro.eterra.check;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class TextTable {

    static List<TableGrid.Cell[]> locate(TextRecognizer reader, Bitmap image) throws Exception {
        // Textul capturilor micșorate are adesea sub 10 pixeli înălțime.
        float scale = Math.max(1f, Math.min(3f, 3500f / Math.max(image.getWidth(), image.getHeight())));
        try {
            return cells(readWords(reader, image, scale), image.getWidth(), image.getHeight());
        } catch (IllegalArgumentException unreadable) {
            float alternate = scale > 1.25f ? 1f : 0.8f;
            return cells(readWords(reader, image, alternate), image.getWidth(), image.getHeight());
        }
    }

    private static List<Word> readWords(TextRecognizer reader, Bitmap image, float scale) throws Exception {
        Bitmap enlarged = Bitmap.createScaledBitmap(image, Math.round(image.getWidth() * scale),
                Math.round(image.getHeight() * scale), true);
        List<Word> words = new ArrayList<>();
        try {
            Text text = Tasks.await(reader.process(InputImage.fromBitmap(enlarged, 0)));
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    for (Text.Element element : line.getElements()) {
                        Rect box = element.getBoundingBox();
                        if (box != null) words.add(new Word(element.getText(),
                                Math.round(box.centerX() / scale), Math.round(box.centerY() / scale),
                                Math.max(1, Math.round(box.height() / scale))));
                    }
                }
            }
        } finally {
            if (enlarged != image) enlarged.recycle();
        }
        return words;
    }

    record Word(String text, int x, int y, int height) {}

    static List<TableGrid.Cell[]> cells(List<Word> words, int width, int height) {
        List<Word> ordered = new ArrayList<>(words);
        ordered.sort(Comparator.comparingInt(Word::x));
        for (Word status : ordered) {
            if (!normalizedWord(status).startsWith("status")) continue;
            int tolerance = Math.max(3, status.height());
            List<Word> header = new ArrayList<>();
            for (Word word : ordered) {
                if (Math.abs(word.y() - status.y()) <= tolerance) header.add(word);
            }
            List<TableGrid.Cell[]> rows = rows(ordered, header, status, width, height);
            if (!rows.isEmpty()) return rows;
        }
        throw new IllegalArgumentException(
                "Nu am putut citi tabelul din imagine. Verifică dacă anteturile și numerele cererilor sunt lizibile.");
    }

    private static List<TableGrid.Cell[]> rows(List<Word> words, List<Word> header,
                                               Word status, int width, int height) {
        Word client = find(header, "client", status.x());
        if (client == null) client = find(header, "beneficiar", status.x());
        if (client == null) return Collections.emptyList();
        Word number = find(header, "cerere", client.x());
        if (number == null || number.x() - client.x() < width * 0.15) {
            return Collections.emptyList();
        }

        int numberCenter = number.x() - Math.round(number.height() * 0.65f);
        int halfWidth = Math.max(8, Math.round((client.x() - status.x()) * 0.25f));
        Word uat = find(header, "uat", client.x());
        Word deadline = find(header, "termen", numberCenter);
        Word deadlineEnd = deadline == null ? null : find(header, "cerere", deadline.x());
        Word flow = find(header, "flux", client.x());
        int deadlineCenter = deadline == null ? -1 : deadline.x();
        if (deadlineEnd != null && deadlineEnd.x() - deadlineCenter < halfWidth) {
            deadlineCenter = (deadlineCenter + deadlineEnd.x()) / 2;
        }
        int[] centers = {status.x(), client.x(), numberCenter, uat == null ? -1 : uat.x(),
                deadlineCenter, flow == null ? -1 : flow.x()};
        Word object = find(header, "obiect", client.x());
        int flowHalfWidth = flow != null && object != null && flow.x() > object.x()
                ? Math.max(halfWidth, Math.round((flow.x() - object.x()) * 0.42f)) : halfWidth;

        List<Word> anchors = new ArrayList<>();
        for (Word word : words) {
            if (word.y() <= status.y() + status.height() * 2) continue;
            boolean requestCandidate = Math.abs(word.x() - numberCenter) < halfWidth
                    && !CaseRow.readNumber(word.text()).isEmpty();
            boolean statusCandidate = Math.abs(word.x() - status.x()) < halfWidth
                    && switch (normalizedWord(word)) {
                        case "lucru", "solutionata", "solutionat", "confirmare", "necesita",
                                "tehnica", "documentatie" -> true;
                        default -> false;
                    };
            if ((requestCandidate || statusCandidate) && word.height() <= status.height() * 1.7) {
                anchors.add(word);
            }
        }
        anchors.sort(Comparator.comparingInt(Word::y));
        List<Integer> rowCenters = new ArrayList<>();
        for (Word word : anchors) {
            if (rowCenters.isEmpty() || word.y() - rowCenters.get(rowCenters.size() - 1)
                    > Math.max(2, status.height() / 2)) rowCenters.add(word.y());
        }
        if (rowCenters.isEmpty()) return Collections.emptyList();

        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < rowCenters.size(); i++) {
            int distance = rowCenters.get(i) - rowCenters.get(i - 1);
            if (distance >= 1.4 * status.height() && distance <= 3 * status.height()) gaps.add(distance);
        }
        gaps.sort(Integer::compareTo);
        int period = gaps.isEmpty() ? 0 : gaps.get(gaps.size() / 2);
        if (period > 0) {
            List<Integer> inferred = new ArrayList<>();
            inferred.add(rowCenters.get(0));
            for (int i = 1; i < rowCenters.size(); i++) {
                int end = rowCenters.get(i);
                int start = rowCenters.get(i - 1);
                int count = Math.round((end - start) / (float) period);
                if (count >= 2 && count <= 8 && Math.abs(end - start - count * period) <= period * 0.25) {
                    for (int step = 1; step < count; step++) {
                        inferred.add(start + Math.round((end - start) * step / (float) count));
                    }
                }
                inferred.add(end);
            }
            rowCenters = inferred;
        }

        int rowMargin = period > 0 ? Math.max(3, (int) (period * 0.44)) : status.height();
        List<TableGrid.Cell[]> result = new ArrayList<>();
        for (int center : rowCenters) {
            int top = Math.max(0, center - rowMargin);
            int bottom = Math.min(height, center + rowMargin);
            TableGrid.Cell[] row = new TableGrid.Cell[centers.length];
            for (int col = 0; col < centers.length; col++) {
                if (centers[col] < 0) continue;
                int columnWidth = col == 5 ? flowHalfWidth : halfWidth;
                int left = Math.max(0, centers[col] - columnWidth);
                int right = Math.min(width, centers[col] + columnWidth);
                row[col] = new TableGrid.Cell(left, top, right, bottom);
            }
            result.add(row);
        }
        return result;
    }

    private static String normalizedWord(Word word) {
        return CaseRow.normalized(word.text()).replaceFirst("^[^a-z]+", "");
    }

    private static Word find(List<Word> header, String prefix, int after) {
        for (Word word : header) {
            if (word.x() > after && normalizedWord(word).startsWith(prefix)) return word;
        }
        return null;
    }
}
