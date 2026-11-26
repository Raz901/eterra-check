package ro.eterra.check;

import java.text.Normalizer;
import java.util.Locale;

record CaseRow(String number, String client, String status) {
    static boolean validNumber(String number) {
        return number.matches("[0-9]+");
    }

    static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\s+", " ");
    }

    static String statusKey(String status) {
        String key = normalized(status);
        if (key.startsWith("necesita completari")) return "necesita completari";
        if (key.startsWith("solutionat")) return "solutionata";
        return key;
    }

    static String displayStatus(String status) {
        return switch (statusKey(status)) {
            case "in lucru" -> "În lucru";
            case "solutionata" -> "Soluționată";
            case "necesita completari" -> "Necesită completări";
            case "documentatie tehnica" -> "Documentație tehnică";
            case "confirmare primire solutie" -> "Confirmare primire soluție";
            default -> status.trim();
        };
    }
}
