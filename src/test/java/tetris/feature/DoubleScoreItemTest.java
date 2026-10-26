package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.data.Board;
import tetris.feature.item.ActiveEffect;
import tetris.feature.item.DoubleScoreEffect;
import tetris.feature.item.GameModifiers;
import tetris.feature.item.ItemResult;
import tetris.feature.item.ItemType;

/**
 * 점수 2배 아이템(D)과 GameModifiers의 점수 배율을 검증한다.
 * D 블록 자신의 Lock부터 다음 블록 10개까지 11번 2배, 다시 얻으면 10으로 되돌림, 4배로 겹치지 않음.
 */
public class DoubleScoreItemTest {
    // GameSession이 Lock 하나를 처리하는 순서를 흉내 낸다: 효과 실행 → 배율 읽기(점수 계산) → Lock 끝
    private static int lockAndReadMultiplier(GameModifiers modifiers, boolean withDoubleScoreItem) {
        if (withDoubleScoreItem) new DoubleScoreEffect().onLock(new Board(), 10, 4, modifiers);
        int multiplier = modifiers.scoreMultiplier();
        modifiers.onLockFinished();
        return multiplier;
    }

    @Test
    void 처음에는_배율_1이고_켜진_효과가_없다() {
        GameModifiers modifiers = new GameModifiers();
        assertEquals(1, modifiers.scoreMultiplier());
        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void D_블록_자신의_Lock부터_11번_2배이고_12번째는_1배() {
        GameModifiers modifiers = new GameModifiers();
        int[] multipliers = new int[13];
        multipliers[0] = lockAndReadMultiplier(modifiers, true);
        for (int i = 1; i < multipliers.length; i++) multipliers[i] = lockAndReadMultiplier(modifiers, false);

        int[] expected = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1};
        assertArrayEquals(expected, multipliers);
    }

    @Test
    void 효과_중에_다시_얻으면_남은_수를_10으로_되돌린다() {
        GameModifiers modifiers = new GameModifiers();
        lockAndReadMultiplier(modifiers, true);
        for (int i = 0; i < 6; i++) lockAndReadMultiplier(modifiers, false); // 남은 수 4

        assertEquals(2, lockAndReadMultiplier(modifiers, true)); // 다시 얻은 블록 자신도 2배
        int doubled = 0;
        for (int i = 0; i < 20 && lockAndReadMultiplier(modifiers, false) == 2; i++) doubled++;
        assertEquals(10, doubled);
    }

    @Test
    void 겹쳐서_얻어도_배율은_2배를_넘지_않는다() {
        GameModifiers modifiers = new GameModifiers();
        new DoubleScoreEffect().onLock(new Board(), 0, 0, modifiers);
        new DoubleScoreEffect().onLock(new Board(), 0, 0, modifiers);
        assertEquals(2, modifiers.scoreMultiplier());
    }

    @Test
    void 화면용_효과_목록에_x2와_남은_블록_수가_나온다() {
        GameModifiers modifiers = new GameModifiers();
        lockAndReadMultiplier(modifiers, true);
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 10)), modifiers.effects());

        lockAndReadMultiplier(modifiers, false);
        assertEquals(List.of(new ActiveEffect(ItemType.DOUBLE_SCORE, 9)), modifiers.effects());

        for (int i = 0; i < 9; i++) lockAndReadMultiplier(modifiers, false);
        assertTrue(modifiers.effects().isEmpty());
    }

    @Test
    void 효과_목록은_바깥에서_바꿀_수_없다() {
        GameModifiers modifiers = new GameModifiers();
        new DoubleScoreEffect().onLock(new Board(), 0, 0, modifiers);
        List<ActiveEffect> effects = modifiers.effects();
        assertThrows(UnsupportedOperationException.class, effects::clear);
    }

    @Test
    void reset하면_효과가_꺼진다() {
        GameModifiers modifiers = new GameModifiers();
        new DoubleScoreEffect().onLock(new Board(), 0, 0, modifiers);
        modifiers.reset();

        assertEquals(1, modifiers.scoreMultiplier());
        assertTrue(modifiers.effects().isEmpty());
        modifiers.onLockFinished(); // 켜진 직후 표시가 남아 있지 않아야 한다
        assertEquals(1, modifiers.scoreMultiplier());
    }

    @Test
    void 효과는_아무것도_지우지_않고_낙하_배율도_바꾸지_않는다() {
        GameModifiers modifiers = new GameModifiers();
        ItemResult result = new DoubleScoreEffect().onLock(new Board(), 5, 5, modifiers);

        assertTrue(result.rows().isEmpty());
        assertTrue(result.cells().isEmpty());
        assertEquals(1.0, modifiers.fallMultiplier());
    }

    @Test
    void ItemType_D는_DoubleScoreEffect를_효과로_갖는다() {
        assertEquals('D', ItemType.DOUBLE_SCORE.symbol());
        assertInstanceOf(DoubleScoreEffect.class, ItemType.DOUBLE_SCORE.effect());
    }
}
