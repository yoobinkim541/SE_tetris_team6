package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.BattleMode;
import tetris.ui.versus.BattleKeyMap;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.FakeBattleClient;
import tetris.ui.versus.PaintLog;

/** S11-B2: 경기 화면의 상태 수신→그리기 기록 (R3-LATENCY) */
class PaintLogTest {
    private static MatchSnapshot revision(long revision) {
        var s = FakeBattleClient.sample();
        return new MatchSnapshot(s.matchId(), revision, s.one(), s.two(), s.remainingMillis(), MatchPhase.PLAYING, null);
    }

    private static void paint(BattlePanel panel) {
        var image = new BufferedImage(960, 720, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
    }

    @Test
    void eachNewSnapshotIsLoggedOnceWithItsReceiveToPaintDelay() {
        var panel = new BattlePanel(false, BattleKeyMap.online().guide(), "일반 대전", BattleMode.CLASSIC, PlayerId.ONE);
        panel.setSize(960, 720);
        long before = System.nanoTime();
        panel.setSnapshot(revision(1));
        paint(panel);
        paint(panel); // 같은 스냅샷을 다시 그린 것은 새 표시가 아니다
        var entries = panel.paintLog().entries();
        assertEquals(1, entries.size());
        PaintLog.Entry first = entries.getFirst();
        assertEquals(1, first.snapshot().revision());
        assertTrue(first.receivedNanos() >= before);
        assertTrue(first.delayNanos() >= 0);
        // 그리기 전에 두 번 받으면 먼저 받은 시각을 기준으로 한다 (가장 오래 기다린 상태)
        panel.setSnapshot(revision(2));
        long firstReceive = System.nanoTime();
        panel.setSnapshot(revision(3));
        paint(panel);
        var last = panel.paintLog().entries().getLast();
        assertEquals(3, last.snapshot().revision());
        assertTrue(last.receivedNanos() <= firstReceive, "revision 2 수신 시각 유지");
        panel.paintLog().clear();
        assertTrue(panel.paintLog().entries().isEmpty());
    }

    @Test
    void logKeepsOnlyTheMostRecentEntries() {
        var panel = new BattlePanel(false);
        panel.setSize(960, 720);
        for (int i = 0; i < PaintLog.CAPACITY + 10; i++) {
            panel.setSnapshot(revision(i));
            paint(panel);
        }
        var entries = panel.paintLog().entries();
        assertEquals(PaintLog.CAPACITY, entries.size());
        assertEquals(10, entries.getFirst().snapshot().revision());
    }
}
