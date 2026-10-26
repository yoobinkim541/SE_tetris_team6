package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.data.Board;
import tetris.feature.item.ActiveEffect;
import tetris.feature.item.DoubleScoreEffect;
import tetris.feature.item.GameModifiers;
import tetris.feature.item.ItemResult;
import tetris.feature.item.ItemType;
import tetris.feature.item.SlowEffect;

/**
 * 슬로우 아이템(S)과 GameModifiers의 낙하 배율을 검증한다.
 * 켜진 뒤 나오는 블록 10개만 1.5배, 다시 얻으면 10으로 되돌림, 점수 2배와 따로 셈.
 */
public class SlowItemTest {
    private static final double DELTA = 1e-9;

    @Test
    void 실제_S_블록_이후_10개만_느리고_Pause와_Restart가_계약을_지킨다() {
        tetris.component.block.Block[] blocks = new tetris.component.block.Block[14];
        for (int i = 0; i < blocks.length; i++) {
            int[][] shape = new int[10][10];
            java.util.Arrays.fill(shape[0], 1);
            blocks[i] = new tetris.component.block.Block(shape) {
                @Override public tetris.component.block.BlockType getType() {
                    return tetris.component.block.BlockType.I;
                }
            };
        }
        blocks[0].attachItem(0, 4, ItemType.SLOW);
        var clock = new GameTestSupport.RecordingClock();
        var session = new tetris.feature.game.GameSession(GameTestSupport.sequence(blocks), clock,
                new GameTestSupport.RecordingListener());
        session.start();
        assertEquals(1000, clock.starts.getLast());
        for (int lock = 0; lock < 11; lock++) {
            session.hardDrop();
            session.pause();
            session.resume();
            assertEquals(150, clock.starts.getLast()); // CLEARING 재개는 잔여 수를 소비하지 않는다
            session.tick();
            int base = tetris.feature.rule.LevelPolicy.fallIntervalMillis(session.snapshot().level());
            int expected = (int) Math.round(base * (lock < 10 ? 1.5 : 1.0));
            assertEquals(expected, clock.starts.getLast());
            var effects = session.snapshot().effects();
            if (lock < 10) assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 9 - lock)), effects);
            else assertTrue(effects.isEmpty());
            session.pause();
            session.resume();
            assertEquals(expected, clock.starts.getLast());
            assertEquals(effects, session.snapshot().effects());
        }
        session.pause();
        session.restart();
        assertTrue(session.snapshot().effects().isEmpty());
        assertEquals(1000, clock.starts.getLast());
    }

    // GameSession이 블록 하나를 처리하는 순서를 흉내 낸다: Spawn → 배율 읽기(타이머) → Lock(효과 실행) → Lock 끝
    private static double spawnAndLock(GameModifiers modifiers, boolean withSlowItem) {
        modifiers.onSpawn();
        modifiers.beginLock();
        double multiplier = modifiers.fallMultiplier();
        if (withSlowItem) new SlowEffect().onLock(new Board(), 10, 4, modifiers);
        modifiers.finishLock();
        return multiplier;
    }

    @Test
    void 처음에는_낙하_배율_1() {
        GameModifiers modifiers = new GameModifiers();
        modifiers.onSpawn();
        modifiers.beginLock();
        assertEquals(1.0, modifiers.fallMultiplier(), DELTA);
    }

    @Test
    void S_블록_자신은_그대로이고_다음_블록_10개가_1_5배_11번째는_원래대로() {
        GameModifiers modifiers = new GameModifiers();
        double[] multipliers = new double[12];
        multipliers[0] = spawnAndLock(modifiers, true);
        for (int i = 1; i < multipliers.length; i++) multipliers[i] = spawnAndLock(modifiers, false);

        double[] expected = {1.0, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.0};
        assertArrayEquals(expected, multipliers, DELTA);
    }

    @Test
    void 효과_중에_다시_얻으면_다음_블록부터_10개로_되돌린다() {
        GameModifiers modifiers = new GameModifiers();
        spawnAndLock(modifiers, true);
        for (int i = 0; i < 4; i++) spawnAndLock(modifiers, false); // 남은 수 6

        assertEquals(1.5, spawnAndLock(modifiers, true), DELTA); // 다시 얻은 블록 자신은 이전 효과로 이미 슬로우
        int slowed = 0;
        for (int i = 0; i < 20 && spawnAndLock(modifiers, false) == 1.5; i++) slowed++;
        assertEquals(10, slowed);
    }

    @Test
    void 겹쳐서_얻어도_배율은_1_5배를_넘지_않는다() {
        GameModifiers modifiers = new GameModifiers();
        new SlowEffect().onLock(new Board(), 0, 0, modifiers);
        new SlowEffect().onLock(new Board(), 0, 0, modifiers);
        modifiers.onSpawn();
        modifiers.beginLock();
        assertEquals(1.5, modifiers.fallMultiplier(), DELTA);
    }

    @Test
    void 점수_2배와_동시에_켜져도_서로_따로_센다() {
        GameModifiers modifiers = new GameModifiers();
        modifiers.onSpawn();
        modifiers.beginLock();
        new DoubleScoreEffect().onLock(new Board(), 0, 0, modifiers);
        new SlowEffect().onLock(new Board(), 0, 0, modifiers);
        modifiers.finishLock();

        for (int i = 0; i < 10; i++) {
            modifiers.onSpawn();
        modifiers.beginLock();
            assertEquals(1.5, modifiers.fallMultiplier(), DELTA);
            assertEquals(2, modifiers.scoreMultiplier());
            modifiers.finishLock();
        }
        modifiers.onSpawn();
        modifiers.beginLock();
        assertEquals(1.0, modifiers.fallMultiplier(), DELTA);
        assertEquals(1, modifiers.scoreMultiplier());
    }

    @Test
    void 슬로우는_점수_배율과_추가_점수를_바꾸지_않는다() {
        GameModifiers modifiers = new GameModifiers();
        ItemResult result = new SlowEffect().onLock(new Board(), 5, 5, modifiers);

        assertTrue(result.rows().isEmpty());
        assertTrue(result.cells().isEmpty());
        assertEquals(1, modifiers.scoreMultiplier());
        assertTrue(modifiers.effects().stream().allMatch(effect -> effect.type() == ItemType.SLOW));
    }

    @Test
    void 화면용_남은_블록_수는_지금_블록_이후_Spawn수를_센다() {
        GameModifiers modifiers = new GameModifiers();
        spawnAndLock(modifiers, true);
        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 10)), modifiers.effects()); // 다음 블록이 나오기 전

        modifiers.onSpawn();
        modifiers.beginLock();
        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 9)), modifiers.effects()); // 첫 슬로우 블록이 떨어지는 중
        modifiers.finishLock();

        for (int i = 0; i < 9; i++) spawnAndLock(modifiers, false);
        modifiers.onSpawn();
        modifiers.beginLock(); // 11번째 블록: 슬로우 아님
        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void 마지막_슬로우_블록은_남은_Spawn_0이어도_느리다() {
        GameModifiers modifiers = new GameModifiers();
        spawnAndLock(modifiers, true);
        for (int i = 0; i < 9; i++) spawnAndLock(modifiers, false);
        modifiers.onSpawn();
        modifiers.beginLock(); // 10번째 슬로우 블록

        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 0)), modifiers.effects());
        assertEquals(1.5, modifiers.fallMultiplier(), DELTA);
    }

    @Test
    void 슬로우_중인_블록이_S를_다시_얻어도_남은_수는_10을_넘지_않는다() {
        GameModifiers modifiers = new GameModifiers();
        spawnAndLock(modifiers, true);
        spawnAndLock(modifiers, false);
        spawnAndLock(modifiers, true); // 슬로우 대상 블록이 S를 얻고 Lock됨

        assertEquals(List.of(new ActiveEffect(ItemType.SLOW, 10)), modifiers.effects());
    }

    @Test
    void 마지막_슬로우_블록_다음_Spawn에서_표시가_빠진다() {
        GameModifiers modifiers = new GameModifiers();
        spawnAndLock(modifiers, true);
        for (int i = 0; i < 10; i++) spawnAndLock(modifiers, false);
        modifiers.onSpawn();

        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void reset하면_떨어지는_블록의_슬로우도_꺼진다() {
        GameModifiers modifiers = new GameModifiers();
        new SlowEffect().onLock(new Board(), 0, 0, modifiers);
        modifiers.onSpawn();
        modifiers.beginLock();
        modifiers = new GameModifiers();

        assertEquals(1.0, modifiers.fallMultiplier(), DELTA);
        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void ItemType_S는_SlowEffect를_효과로_갖는다() {
        assertEquals('S', ItemType.SLOW.symbol());
        assertInstanceOf(SlowEffect.class, ItemType.SLOW.effect());
    }
}
