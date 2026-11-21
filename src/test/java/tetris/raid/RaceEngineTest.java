package tetris.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.component.block.OBlock;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.data.Board;
import tetris.feature.game.GameSession;
import tetris.feature.raid.RaidEngine;
import tetris.feature.raid.RaidRules;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

@Timeout(60)
class RaceEngineTest {
    @Test
    void firstKillSendsTwoRowsAndAdvancesBoth() throws Exception {
        Harness harness = race(11);
        harness.ready();
        assertEquals(165, harness.engine.snapshot().bossFor(0).maxHp());
        assertEquals(165, harness.engine.snapshot().bossFor(1).maxHp());
        assertEquals(harness.engine.snapshot().bossFor(0).id(), harness.engine.snapshot().bossFor(1).id());
        setHp(harness.engine, 0, 25);
        harness.clearTwo(1);
        int slowerHp = harness.engine.snapshot().bossFor(1).hp();
        assertTrue(slowerHp < harness.engine.snapshot().bossFor(1).maxHp());
        harness.clearTwo(0);

        RaidSnapshot after = harness.engine.snapshot();
        assertEquals(RaidPhase.PLAYING, after.phase(), "레이스는 카드를 고르지 않는다");
        assertNull(after.offer());
        assertEquals(1, after.raceKills()[0]);
        assertEquals(0, after.raceKills()[1]);
        assertEquals(after.bossFor(0).id(), after.bossFor(1).id());
        assertEquals(2, after.bossFor(0).level());
        assertEquals(after.bossFor(0).maxHp(), after.bossFor(0).hp());
        assertEquals(after.bossFor(1).maxHp(), after.bossFor(1).hp(), "느린 쪽 보스는 교체된다");
        assertNotEquals(slowerHp, after.bossFor(1).hp());
        assertEquals(2, after.players().get(1).board().pendingRows().size());
        assertEquals(0, harness.event(RaidEvent.Type.BOSS_DEFEATED).player());
    }

    @Test
    void topOutEndsWithTheOtherAsWinner() throws Exception {
        Harness harness = race(4);
        harness.ready();
        for (int i = 0; i < 240 && harness.engine.snapshot().phase() != RaidPhase.FINISHED; i++) harness.drop(0);
        RaidSnapshot end = harness.engine.snapshot();
        assertEquals(RaidPhase.FINISHED, end.phase());
        assertEquals(1, end.result().winner());
        assertTrue(end.events().stream().anyMatch(event -> event.type() == RaidEvent.Type.PLAYER_DOWN)
                || !end.players().get(0).alive());
    }

    @Test
    void timeoutUsesKillsThenHpProgressThenDraw() throws Exception {
        Harness draw = race(8);
        draw.ready();
        draw.now = RaidRules.COUNTDOWN_MILLIS + RaidRules.RACE_LIMIT_MILLIS;
        draw.engine.advance(draw.now);
        assertEquals(RaidPhase.FINISHED, draw.engine.snapshot().phase());
        assertNull(draw.engine.snapshot().result().winner());

        Harness progress = race(9);
        progress.ready();
        progress.clearTwo(0);
        progress.now = RaidRules.COUNTDOWN_MILLIS + RaidRules.RACE_LIMIT_MILLIS;
        progress.engine.advance(progress.now);
        assertEquals(0, progress.engine.snapshot().result().winner());

        Harness kills = race(12);
        kills.ready();
        setHp(kills.engine, 0, 25);
        kills.clearTwo(0);
        assertEquals(1, kills.engine.snapshot().raceKills()[0]);
        kills.clearTwo(1);
        kills.now = RaidRules.COUNTDOWN_MILLIS + RaidRules.RACE_LIMIT_MILLIS;
        kills.engine.advance(kills.now);
        assertEquals(0, kills.engine.snapshot().result().winner(), "처치 수가 남은 HP 비율보다 앞선다");
    }

    private static Harness race(long seed) {
        Harness harness = null;
        for (long candidate = seed; candidate < seed + 40; candidate++) {
            Harness next = new Harness(candidate);
            if (next.engine.snapshot().boss().id() != BossId.GOLDEN_TETRO) return next;
            harness = next;
        }
        return harness;
    }

    private static void setHp(RaidEngine engine, int player, int hp) throws Exception {
        Field lanes = RaidEngine.class.getDeclaredField("lanes");
        lanes.setAccessible(true);
        Object lane = Array.get(lanes.get(engine), player);
        Field field = lane.getClass().getDeclaredField("hp");
        field.setAccessible(true);
        field.setInt(lane, hp);
    }

    private static final class Harness {
        final long[] sequence = new long[2];
        long now;
        final MatchClock clock = () -> now;
        final RaidEngine engine;

        Harness(long seed) {
            engine = new RaidEngine(new RaidConfig(RaidMode.RACE, seed, ControlStyle.CLASSIC), clock,
                    player -> OBlock::new, (boss, level, previous, before, random) -> PatternId.ROCK_THROW);
        }

        void ready() {
            now = RaidRules.COUNTDOWN_MILLIS;
            engine.advance(now);
        }

        void drop(int player) {
            PlayerId id = player == 0 ? PlayerId.ONE : PlayerId.TWO;
            engine.submit(new PlayerCommand(id, GameAction.HARD_DROP, false, sequence[player]++));
            engine.submit(new PlayerCommand(id, GameAction.HARD_DROP, true, sequence[player]++));
            now += 1;
            engine.advance(now);
        }

        void clearTwo(int player) {
            try {
                GameSession session = session(player);
                Board board = board(session);
                for (int row : new int[] {18, 19}) {
                    for (int col = 0; col < 10; col++) {
                        if (col == 4 || col == 5 || !board.isEmpty(row, col)) continue;
                        assertTrue(board.tryPlaceBlock(cell(row, col)));
                    }
                }
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            drop(player);
            now += 150;
            engine.advance(now);
        }

        RaidEvent event(RaidEvent.Type type) {
            for (RaidEvent event : engine.snapshot().events()) if (event.type() == type) return event;
            return null;
        }

        private GameSession session(int player) throws Exception {
            Field field = RaidEngine.class.getDeclaredField("sessions");
            field.setAccessible(true);
            return ((GameSession[]) field.get(engine))[player];
        }

        private static Board board(GameSession session) throws Exception {
            Field field = GameSession.class.getDeclaredField("board");
            field.setAccessible(true);
            return (Board) field.get(session);
        }

        private static Block cell(int row, int col) {
            Block block = new Block(new int[][] {{1}}) {
                @Override public BlockType getType() { return BlockType.O; }
            };
            block.setPosition(row, col);
            return block;
        }
    }
}
