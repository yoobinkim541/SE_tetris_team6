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
    public static final int PROBE_INTERVAL_MILLIS = 50;
    private static final double THRESHOLD_MILLIS = 200.0;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final LobbyClient lobby;
    private final Path outputDirectory;
    private final Supplier<LocalDateTime> now;
    private final List<LatencySample> samples = new ArrayList<>();
    private final JLabel statsLabel = new JLabel();
    private final JLabel fileLabel = new JLabel(" ");
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
        render();
        subscription = lobby.subscribeRtt(this::accept);
    }

    /** 새 측정: 이전 표본을 비우고 PROBE_INTERVAL_MILLIS 간격으로 PROBE_COUNT번 PING을 보낸다 */
    public void measure() {
        samples.clear();
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
        render();
    }

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
