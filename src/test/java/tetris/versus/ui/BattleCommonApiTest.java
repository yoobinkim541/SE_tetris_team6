package tetris.versus.ui;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.*;
import tetris.ui.versus.BattleInputRouter;
import tetris.ui.versus.BattleKeyMap;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.FakeBattleClient;
import static org.junit.jupiter.api.Assertions.*;

class BattleCommonApiTest {
    @Test void commonPortConnectsRouterFakeAndViewWithoutAdapters() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fake = new FakeBattleClient()) {
                BattleClient port = fake;
                var view = new BattlePanel(false);
                var received = new ArrayList<MatchSnapshot>();
                try (var subscription = port.subscribe(snapshot -> {
                    received.add(snapshot);
                    view.setSnapshot(snapshot);
                })) {
                    assertEquals(MatchPhase.PLAYING, received.getFirst().phase());
                    assertEquals(VersusRules.MAX_PENDING_ROWS, received.getFirst().two().pendingRows().size());
                    var root = new JRootPane();
                    try (var router = new BattleInputRouter(root, new BattleKeyMap(), port::send, a -> true)) {
                        Object binding = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                            .get(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false));
                        root.getActionMap().get(binding).actionPerformed(new ActionEvent(root, 0, "press"));
                        assertEquals(new PlayerCommand(PlayerId.TWO, GameAction.MOVE_LEFT, true, 0), fake.commands().getFirst());
                    }
                    MatchSnapshot initial = received.getFirst();
                    fake.publish(new MatchSnapshot("finished", 1, initial.one(), initial.two(), 0,
                        MatchPhase.FINISHED, MatchResult.win(PlayerId.TWO, MatchResult.EndReason.TIME_UP)));
                    assertEquals(PlayerId.TWO, received.getLast().result().winner());
                    assertEquals("P2 승리 · 시간 종료", BattlePanel.statusText(received.getLast()));
                    assertEquals(2, received.size());
                }
            }
        });
    }

    @Test void statusCoversEveryPhaseAndResultReason() {
        var sample = FakeBattleClient.sample();
        assertEquals("곧 시작합니다", BattlePanel.statusText(snapshot(sample, MatchPhase.COUNTDOWN, null)));
        assertEquals("경기 중", BattlePanel.statusText(sample));
        assertEquals("일시정지", BattlePanel.statusText(snapshot(sample, MatchPhase.PAUSED, null)));
        assertEquals("무승부 · 블록 쌓임", BattlePanel.statusText(snapshot(sample, MatchPhase.FINISHED,
            MatchResult.draw(MatchResult.EndReason.TOPPED_OUT))));
        assertEquals("무승부 · 시간 종료", BattlePanel.statusText(snapshot(sample, MatchPhase.FINISHED,
            MatchResult.draw(MatchResult.EndReason.TIME_UP))));
        assertEquals("P1 승리 · 기권", BattlePanel.statusText(snapshot(sample, MatchPhase.FINISHED,
            MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT))));
    }

    private static MatchSnapshot snapshot(MatchSnapshot sample, MatchPhase phase, MatchResult result) {
        return new MatchSnapshot(sample.matchId(), 1, sample.one(), sample.two(), sample.remainingMillis(), phase, result);
    }
}
