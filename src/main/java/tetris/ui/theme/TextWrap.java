package tetris.ui.theme;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/** 카드 설명. 괄호 안과 숫자·단위 사이는 자르지 않는다. */
public final class TextWrap {
    private TextWrap() {
    }

    public static List<String> lines(String text, FontMetrics metrics, int width, int maxLines) {
        List<String> tokens = tokens(text);
        List<String> lines = new ArrayList<>();
        if (tokens.isEmpty() || metrics == null || width <= 0) return lines;
        StringBuilder line = new StringBuilder();
        for (String token : tokens) {
            String trial = line.isEmpty() ? token : line + " " + token;
            if (!line.isEmpty() && metrics.stringWidth(trial) > width && lines.size() + 1 < maxLines) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(token);
            } else if (!line.isEmpty() && metrics.stringWidth(trial) > width) {
                line.append(' ').append(token);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(token);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    public static boolean overflows(List<String> lines, FontMetrics metrics, int width, int maxLines) {
        if (lines.size() > maxLines) return true;
        for (String line : lines) {
            if (metrics.stringWidth(line) > width) return true;
        }
        return false;
    }

    static List<String> tokens(String text) {
        List<String> raw = new ArrayList<>();
        if (text == null || text.isEmpty()) return raw;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ') {
                i++;
                continue;
            }
            if (c == '(') {
                int end = text.indexOf(')', i);
                if (end < 0) end = text.length() - 1;
                raw.add(text.substring(i, end + 1));
                i = end + 1;
                continue;
            }
            int j = i + 1;
            while (j < text.length() && text.charAt(j) != ' ' && text.charAt(j) != '(') j++;
            raw.add(text.substring(i, j));
            i = j;
        }
        List<String> merged = new ArrayList<>();
        for (int n = 0; n < raw.size(); n++) {
            if (n + 1 < raw.size() && number(raw.get(n)) && unit(raw.get(n + 1))) {
                merged.add(raw.get(n) + " " + raw.get(n + 1));
                n++;
            } else {
                merged.add(raw.get(n));
            }
        }
        return merged;
    }

    private static boolean number(String token) {
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    private static boolean unit(String token) {
        return token.equals("마리") || token.equals("배") || token.equals("줄") || token.equals("개")
                || token.equals("회") || token.equals("조각") || token.equals("%");
    }
}
