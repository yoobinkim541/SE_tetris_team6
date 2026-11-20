package tetris.ui.raid;

import java.util.List;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;

/** 화면 테스트·미리보기용 스냅샷. 엔진 없이 보드를 채운다 */
public final class RaidSamples {
    private RaidSamples() {
    }

    public static RaidSnapshot playing(RaidMode mode) {
        return snapshot(mode, RaidPhase.PLAYING, BossId.STONE_GOLEM, 2, 412, 580, PatternId.ROCK_THROW,
                0, 3, new int[0], -1, 3, 192_000L, 62, null, List.of(), null);
    }

    public static RaidSnapshot phase(RaidMode mode, RaidPhase phase) {
        RaidSnapshot.CardOffer offer = phase == RaidPhase.CARD_PICK
                ? new RaidSnapshot.CardOffer(List.of(CardId.SHARP_BLOCKS, CardId.SHIELD, CardId.LUCK), mode == RaidMode.SOLO ? 0 : 1, 4200)
                : null;
        RaidSnapshot.Result result = phase == RaidPhase.FINISHED
                ? new RaidSnapshot.Result(4, 1580, 192_000L) : null;
        List<RaidEvent> events = phase == RaidPhase.PLAYING
                ? List.of(new RaidEvent(RaidEvent.Type.DAMAGE, 0, 70),
                        new RaidEvent(RaidEvent.Type.COMBO, 0, 3),
                        new RaidEvent(RaidEvent.Type.TSPIN, 0, 0),
                        new RaidEvent(RaidEvent.Type.CRITICAL, 0, 90),
                        new RaidEvent(RaidEvent.Type.BLOCKED, 1, 2),
                        new RaidEvent(RaidEvent.Type.REVIVED, 1, 0))
                : List.of();
        return snapshot(mode, phase, BossId.STONE_GOLEM, 2, 412, 580, PatternId.ROCK_THROW,
                mode == RaidMode.SOLO ? 0 : 0, 3, new int[0], -1, 3, 192_000L, 62, offer, events, result);
    }

    public static RaidSnapshot snapshot(RaidMode mode, RaidPhase phase, BossId boss, int level, int hp, int maxHp,
                                        PatternId pattern, int target, int piecesLeft, int[] splitHp, long fleeMillis,
                                        int kills, long elapsed, int gauge, RaidSnapshot.CardOffer offer,
                                        List<RaidEvent> events, RaidSnapshot.Result result) {
        boolean coop = mode == RaidMode.COOP;
        var players = coop
                ? List.of(fighter(true), downed())
                : List.of(fighter(true));
        return new RaidSnapshot("raid-preview", 1, mode, phase,
                new RaidSnapshot.BossView(boss, level, hp, maxHp, pattern, target, piecesLeft, splitHp, fleeMillis),
                players, kills, elapsed, gauge, offer, events, result);
    }

    public static RaidSnapshot.PlayerView fighter(boolean lucky) {
        return new RaidSnapshot.PlayerView(board(), true, luckyGrid(lucky), List.of(
                new RaidSnapshot.Effect(PatternId.CLOUD, 4, new int[] {8, 2, 4, 3}),
                new RaidSnapshot.Effect(PatternId.SEAL_PREVIEW, 3, new int[0]),
                new RaidSnapshot.Effect(PatternId.REVERSE, 2, new int[0]),
                new RaidSnapshot.Effect(PatternId.OVERLOAD, 5, new int[0])),
                0, 8, 640);
    }

    public static RaidSnapshot.PlayerView downed() {
        return new RaidSnapshot.PlayerView(board(), false, new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS],
                List.of(), 3, 8, 220);
    }

    public static MatchSnapshot.PlayerView board() {
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        cells[17][3] = 1;
        cells[17][4] = 1;
        cells[17][5] = 1;
        cells[17][6] = 1;
        cells[18][0] = 3;
        cells[18][1] = 3;
        cells[19][1] = 3;
        cells[19][2] = 3;
        cells[19][8] = 5;
        cells[19][9] = VersusRules.ATTACK_CELL;
        int[][] i = {{0, 1, 0, 0}, {0, 1, 0, 0}, {0, 1, 0, 0}, {0, 1, 0, 0}};
        int[][] o = {{1, 1, 0, 0}, {1, 1, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        int[][] t = {{0, 2, 0, 0}, {2, 2, 2, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        int[][] ghost = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        ghost[19][4] = 1;
        ghost[19][5] = 1;
        return new MatchSnapshot.PlayerView(cells, i, 1200, List.of(new int[] {9, 9, 0, 9, 9, 0, 9, 9, 0, 9}),
                new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS], o, ghost, List.of(i, o, t));
    }

    private static int[][] luckyGrid(boolean on) {
        int[][] lucky = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        if (on) {
            lucky[17][4] = 1;
            lucky[17][5] = 1;
        }
        return lucky;
    }
}
