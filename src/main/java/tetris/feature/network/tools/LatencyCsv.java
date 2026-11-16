package tetris.feature.network.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 지연 측정 표본 CSV 입출력 (S11-C1, R3-LATENCY).
 */
public final class LatencyCsv {
    public static final String HEADER = "mode,direction,sequence,inputNanos,localShownNanos,remoteShownNanos";

    private LatencyCsv() {
    }

    public static void write(Path path, List<LatencySample> samples) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(samples, "samples");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>(samples.size() + 1);
        lines.add(HEADER);
        for (LatencySample sample : samples) {
            Objects.requireNonNull(sample, "sample");
            lines.add(sample.mode()
                    + "," + sample.direction()
                    + "," + sample.sequence()
                    + "," + sample.inputNanos()
                    + "," + sample.localShownNanos()
                    + "," + sample.remoteShownNanos());
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    public static List<LatencySample> read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0).trim())) {
            throw new IOException("Expected CSV header '" + HEADER + "' in " + path);
        }
        List<LatencySample> samples = new ArrayList<>(Math.max(0, lines.size() - 1));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length != 6) {
                throw new IOException("Invalid column count at line " + (i + 1) + ": " + line);
            }
            try {
                String mode = cols[0].trim();
                String direction = cols[1].trim();
                long sequence = Long.parseLong(cols[2].trim());
                long inputNanos = Long.parseLong(cols[3].trim());
                long localShownNanos = Long.parseLong(cols[4].trim());
                long remoteShownNanos = Long.parseLong(cols[5].trim());
                samples.add(new LatencySample(
                        mode, direction, sequence, inputNanos, localShownNanos, remoteShownNanos));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid sample at line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(samples);
    }
}
