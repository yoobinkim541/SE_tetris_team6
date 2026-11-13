package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.*;
import tetris.ui.versus.BattleKeyMap;
import tetris.ui.versus.BattlePanel;
import tetris.ui.versus.BattleResultPanel;
import tetris.ui.versus.FakeBattleClient;

/** S10-B1: P2P 두 보드의 자기/상대 관점, 3모드 범례, 일시정지·결과 표시 (R3-LIVE, R3-MODES, R3-TIME) */
class BattlePerspectiveTest {
    private static MatchSnapshot with(MatchPhase phase, MatchResult result) {
        var sample = FakeBattleClient.sample();
        return new MatchSnapshot(sample.matchId(), 1, sample.one(), sample.two(), 61_000, phase, result);
    }

    @Test
    void boardTitlesMarkTheLocalPlayerOnlyOnline() {
        assertEquals("P1", BattlePanel.boardTitle(PlayerId.ONE, null));
        assertEquals("P2", BattlePanel.boardTitle(PlayerId.TWO, null));
        assertEquals("P1 (나)", BattlePanel.boardTitle(PlayerId.ONE, PlayerId.ONE));
        assertEquals("P2 (상대)", BattlePanel.boardTitle(PlayerId.TWO, PlayerId.ONE));
        assertEquals("P1 (상대)", BattlePanel.boardTitle(PlayerId.ONE, PlayerId.TWO));
        assertEquals("P2 (나)", BattlePanel.boardTitle(PlayerId.TWO, PlayerId.TWO));
    }

    @Test
    void hostAndClientSeeTheSameResultFromTheirOwnSide() {
        var p2Wins = with(MatchPhase.FINISHED, MatchResult.win(PlayerId.TWO, MatchResult.EndReason.TIME_UP));
        assertEquals("패배 · 시간 종료", BattlePanel.statusText(p2Wins, PlayerId.ONE));
        assertEquals("승리! · 시간 종료", BattlePanel.statusText(p2Wins, PlayerId.TWO));
        assertEquals("P2 승리 · 시간 종료", BattlePanel.statusText(p2Wins));
        var draw = with(MatchPhase.FINISHED, MatchResult.draw(MatchResult.EndReason.TOPPED_OUT));
        assertEquals("무승부 · 블록 쌓임", BattlePanel.statusText(draw, PlayerId.TWO));
    }

    @Test
    void legendShowsItemSymbolsOnlyInTheItemBattle() {
        assertTrue(BattlePanel.legendText(BattleMode.ITEM).contains("L 줄 삭제"));
        assertFalse(BattlePanel.legendText(BattleMode.CLASSIC).contains("아이템"));
        assertFalse(BattlePanel.legendText(BattleMode.TIMED).contains("아이템"));
        assertTrue(BattlePanel.legendText(null).contains("아이템"), "모드를 모르면 모두 보인다");
        for (BattleMode mode : BattleMode.values()) assertTrue(BattlePanel.legendText(mode).contains("공격"));
    }

    @Test
    void pauseAndCountdownCoverBothBoardsButPlayDoesNot() {
        assertEquals("일시정지", BattlePanel.overlayText(with(MatchPhase.PAUSED, null)));
        assertEquals("준비", BattlePanel.overlayText(with(MatchPhase.COUNTDOWN, null)));
        assertEquals("", BattlePanel.overlayText(with(MatchPhase.PLAYING, null)));
    }

    @Test
    void everyModeAndPhasePaintsForBothViewpoints() {
        PlayerId[] viewers = {null, PlayerId.ONE, PlayerId.TWO};
        MatchSnapshot[] snapshots = {with(MatchPhase.COUNTDOWN, null), with(MatchPhase.PLAYING, null),
                with(MatchPhase.PAUSED, null),
                with(MatchPhase.FINISHED, MatchResult.win(PlayerId.ONE, MatchResult.EndReason.FORFEIT))};
        for (BattleMode mode : BattleMode.values()) {
            for (PlayerId viewer : viewers) {
                var panel = new BattlePanel(true, BattleKeyMap.online().guide(), "모드", mode, viewer);
                assertEquals(viewer, panel.viewer());
                panel.setSize(960, 720);
                for (MatchSnapshot snapshot : snapshots) {
                    panel.setSnapshot(snapshot);
                    var image = new BufferedImage(960, 720, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics();
                    try { panel.paint(g); } finally { g.dispose(); }
                }
            }
        }
    }

    @Test
    void onlineResultUsesTheViewerAndKeepsLocalWording() {
        var result = new BattleResultPanel("로비로", () -> { }, () -> { });
        var p1Wins = with(MatchPhase.FINISHED, MatchResult.win(PlayerId.ONE, MatchResult.EndReason.TOPPED_OUT));
        result.setSnapshot(p1Wins, "일반 대전 · P2P", PlayerId.TWO);
        assertEquals("일반 대전 · P2P · 패배 · 블록 쌓임", result.getSummaryText());
        assertTrue(result.getScoresText().startsWith("P1 (상대): "), result.getScoresText());
        assertTrue(result.getScoresText().contains("P2 (나): "), result.getScoresText());
        result.setSnapshot(p1Wins, "일반 대전");
        assertEquals("일반 대전 · P1 승리 · 블록 쌓임", result.getSummaryText());
        assertTrue(result.getScoresText().startsWith("P1: "));
    }
}
