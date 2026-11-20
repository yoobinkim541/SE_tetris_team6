package tetris.ui.versus;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import tetris.feature.network.lobby.LobbyClient;
import tetris.feature.network.tools.LatencyCsv;
import tetris.feature.network.tools.LatencySample;
import tetris.feature.network.tools.LatencyStats;

/**
 * 개발자 모드 전용 P2P 로비 측정 패널. 연결된 상대와의 PING/PONG 왕복(RTT)을 모아 보여 주고,
 * 200회 측정과 CSV 저장(NetTool client와 같은 형식)을 버튼으로 한다. 일반 실행에서는 만들지 않는다.
 */
public final class DevRttPanel extends JPanel implements AutoCloseable {
    public static final int PROBE_COUNT = 200;
    /** 자동 기록(측정 중이 아닐 때)이 유지하는 표본 수. 이보다 오래된 것은 버린다 (#188 후속) */
    public static final int AUTO_SAMPLE_LIMIT = 200;
    public static final int PROBE_INTERVAL_MILLIS = 50;
    private static final double THRESHOLD_MILLIS = 200.0;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final LobbyClient lobby;
    private final Path outputDirectory;
    private final Supplier<LocalDateTime> now;
    private final List<LatencySample> samples = new ArrayList<>();
    /** 측정 표본 개수. 이 앞쪽 표본은 자동 기록 제한으로 지우지 않는다 */
    private int protectedSamples;
    /** "200회 측정"으로 아직 받지 않은 PONG 수. 0이면 자동 기록 모드다 */
    private int measuringLeft;
    private final JLabel statsLabel = new JLabel();
    private final JLabel fileLabel = new JLabel(" ");
    /** 개발자 연결 상태 4줄. RTT 통계 아래에 붙고 비어 있으면 숨긴다 (dev-net-overlay) */
    private final JLabel debugLabel = new JLabel();
    private List<String> debugLines = List.of();
    private final JButton measureButton = new JButton("RTT " + PROBE_COUNT + "회 측정");
    private final JButton saveButton = new JButton("CSV 저장");
    private final LobbyClient.Subscription subscription;

    public DevRttPanel(LobbyClient lobby, Path outputDirectory) {
        this(lobby, outputDirectory, LocalDateTime::now);
    }

    /** now: CSV 파일 이름의 시각 (테스트는 고정 시각을 넣는다) */
    public DevRttPanel(LobbyClient lobby, Path outputDirectory, Supplier<LocalDateTime> now) {
        this.lobby = Objects.requireNonNull(lobby, "lobby");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        this.now = Objects.requireNonNull(now, "now");
        setLayout(new FlowLayout(FlowLayout.CENTER, 8, 4));
        setBorder(BorderFactory.createTitledBorder("개발자 모드 · 네트워크 측정"));
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.PLAIN, 14f));
        fileLabel.setForeground(Color.GRAY);
        measureButton.setFocusable(false);
        saveButton.setFocusable(false);
        measureButton.addActionListener(e -> measure());
        saveButton.addActionListener(e -> save());
        add(statsLabel);
        add(measureButton);
        add(saveButton);
        add(fileLabel);
        debugLabel.setForeground(new Color(0xB9F6CA));
        debugLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        debugLabel.setVisible(false);
        add(debugLabel);
        render();
        subscription = lobby.subscribeRtt(this::accept);
    }

    /** 새 측정: 이전 표본을 비우고 PROBE_INTERVAL_MILLIS 간격으로 PROBE_COUNT번 PING을 보낸다 */
    public void measure() {
        samples.clear();
        protectedSamples = 0;
        measuringLeft = PROBE_COUNT;
        fileLabel.setText(" ");
        lobby.probeRtt(PROBE_COUNT, PROBE_INTERVAL_MILLIS);
        render();
    }

    /** 지금까지 모은 표본을 CSV로 저장하고 경로를 돌려준다. 표본이 없으면 null */
    public Path save() {
        if (samples.isEmpty()) {
            fileLabel.setText("저장할 표본이 없습니다. 상대와 연결한 뒤 측정하세요.");
            return null;
        }
        Path file = outputDirectory.resolve("rtt-" + FILE_TIME.format(now.get()) + ".csv");
        try {
            LatencyCsv.write(file, List.copyOf(samples));
            fileLabel.setText("저장: " + file);
            return file;
        } catch (IOException e) {
            fileLabel.setText("저장 실패: " + e.getMessage());
            return null;
        }
    }

    void accept(LatencySample sample) {
        samples.add(Objects.requireNonNull(sample, "sample"));
        if (measuringLeft > 0) {
            // 측정 중 표본은 200개 제한으로 자르지 않는다. PROBE_COUNT개가 모이면 자동 기록으로 돌아간다
            measuringLeft--;
            protectedSamples = samples.size();
        } else {
            // 자동 기록은 최근 AUTO_SAMPLE_LIMIT개만 유지한다. 측정 표본은 보존하고 자동 표본부터 버린다
            while (samples.size() - protectedSamples > AUTO_SAMPLE_LIMIT) samples.remove(protectedSamples);
        }
        render();
    }

    /** 연결 상태 줄을 RTT 통계 아래에 보여 준다. 빈 목록이면 라벨을 숨긴다 */
    public void setDebugLines(List<String> lines) {
        debugLines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (debugLines.isEmpty()) {
            debugLabel.setText("");
            debugLabel.setVisible(false);
        } else {
            // 여러 줄을 한 라벨에 넣는다. 줄 사이는 HTML <br>로 바꾼다
            debugLabel.setText("<html>" + String.join("<br>", debugLines.stream()
                    .map(line -> line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .toList()) + "</html>");
            debugLabel.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public List<String> debugLines() { return debugLines; }
    public JLabel debugLabel() { return debugLabel; }

    private void render() {
        if (samples.isEmpty()) {
            statsLabel.setText("RTT 표본 없음 (연결되면 0.5초마다 자동 기록)");
            return;
        }
        List<Double> millis = samples.stream().map(LatencySample::localMillis).toList();
        LatencyStats stats = LatencyStats.of(millis, THRESHOLD_MILLIS);
        statsLabel.setText(String.format("RTT 표본 %d · 최근 %.1fms · p50 %.1f · p95 %.1f · 최대 %.1fms · %.0fms 초과 %d건",
                stats.count(), millis.getLast(), stats.p50(), stats.p95(), stats.max(),
                stats.thresholdMillis(), stats.overThreshold()));
    }

    public String statsText() { return statsLabel.getText(); }
    public String fileText() { return fileLabel.getText(); }
    public JButton measureButton() { return measureButton; }
    public JButton saveButton() { return saveButton; }
    public List<LatencySample> samples() { return List.copyOf(samples); }

    @Override
    public void close() {
        subscription.close();
    }
}
