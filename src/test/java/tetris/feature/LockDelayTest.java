package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.sequence;

import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.debug.AsciiBoard;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.rule.ImmediateLockPolicy;
import tetris.feature.rule.LockDelayPolicy;
import tetris.feature.versus.api.LockResult;
import tetris.feature.versus.api.SpawnBoundary;

/** LCK-6 (착지 후 0.5초 유예, 이동·회전 성공마다 최대 15회 초기화), ROT-7 (마지막 행동 회전 플래그) */
public class LockDelayTest {
    private static final int DELAY = LockDelayPolicy.DEFAULT_DELAY_MILLIS;
    private static final int FALL = 1000; // NORMAL Level 1 낙하 간격

    private final RecordingClock clock = new RecordingClock();

    private GameSession started(LockDelayPolicyChoice choice, tetris.component.block.Block... blocks) {
        GameSession session = choice == LockDelayPolicyChoice.IMMEDIATE
                ? new GameSession(GameMode.CLASSIC, Difficulty.NORMAL, sequence(blocks), clock, new RecordingListener(),
                        new ImmediateLockPolicy())
                : new GameSession(sequence(blocks), clock, new RecordingListener());
        session.start();
        return session;
    }

    private enum LockDelayPolicyChoice { DEFAULT, IMMEDIATE }

    private GameSession started(tetris.component.block.Block... blocks) {
        return started(LockDelayPolicyChoice.DEFAULT, blocks);
    }

    private static void tick(GameSession session, int times) {
        for (int i = 0; i < times; i++) session.tick();
    }

    private static String row(GameSession session, int row) {
        return AsciiBoard.row(session.snapshot(), row);
    }

    private int lastStart() {
        return clock.starts.get(clock.starts.size() - 1);
    }

    //#region 정책 값
    @Test
    void 기본_유예는_0_5초_최대_15회() {
        LockDelayPolicy policy = new LockDelayPolicy();
        assertEquals(500, policy.lockDelayMillis());
        assertEquals(15, policy.maxResets());
        assertTrue(policy.hasDelay());
        assertThrows(IllegalArgumentException.class, () -> new LockDelayPolicy(0, 15));
        assertThrows(IllegalArgumentException.class, () -> new LockDelayPolicy(500, -1));
    }
    //#endregion

    //#region 착지 유예
    @Test
    void 바닥에_닿는_순간_유예를_시작하고_다음_Tick에_고정한다() {
        GameSession session = started(new OBlock(), new TBlock());
        tick(session, 18);                       // 18행에 착지
        assertTrue(session.isLockDelayActive());
        assertEquals(DELAY, lastStart());
        assertEquals("....oo....", row(session, 19));

        session.tick();                          // 0.5초 뒤
        assertEquals("....OO....", row(session, 19));
        assertFalse(session.isLockDelayActive());
        assertEquals(FALL, lastStart());         // 다음 블록 Spawn
    }

    @Test
    void 유예_중_좌우_이동에_성공하면_타이머를_다시_센다() {
        GameSession session = started(new OBlock(), new TBlock());
        tick(session, 18);
        int before = clock.starts.size();
        session.moveLeft();
        assertEquals(before + 1, clock.starts.size());
        assertEquals(DELAY, lastStart());
        assertEquals("...oo.....", row(session, 19)); // 아직 굳지 않았다
    }

    @Test
    void 유예_중_회전에_성공해도_타이머를_다시_센다() {
        GameSession session = started(new TBlock(), new OBlock());
        session.rotateRight();                   // 공중에서 세운다 (3행 차지)
        tick(session, 17);                       // 17~19행 착지
        assertTrue(session.isLockDelayActive());
        int before = clock.starts.size();
        session.rotateRight();                   // 바닥에서 꼭지 아래로 회전 (18~19행)
        assertEquals(before + 1, clock.starts.size());
        assertEquals(DELAY, lastStart());
    }

    @Test
    void 실패한_이동은_타이머를_다시_세지_않는다() {
        GameSession session = started(new OBlock(), new TBlock());
        tick(session, 18);
        for (int i = 0; i < 4; i++) session.moveLeft(); // 0열까지
        int before = clock.starts.size();
        session.moveLeft();                      // 벽
        assertEquals(before, clock.starts.size());
    }

    @Test
    void 초기화는_블록당_최대_15회() {
        GameSession session = started(new OBlock(), new TBlock());
        tick(session, 18);
        int before = clock.starts.size();
        for (int i = 0; i < 20; i++) {
            if (i % 2 == 0) session.moveLeft(); else session.moveRight();
        }
        assertEquals(before + 15, clock.starts.size()); // 16번째부터는 타이머를 건드리지 않는다
        session.tick();                                  // 마지막 초기화 뒤 0.5초
        assertEquals(GameState.PLAYING, session.getState());
        assertEquals("....OO....", row(session, 19));
    }

    @Test
    void 이동으로_바닥에서_벗어나면_낙하_타이머로_돌아간다() {
        GameSession session = started(new OBlock(), new OBlock(), new TBlock());
        tick(session, 19);                       // 첫 O: 4~5열 18~19행 고정
        tick(session, 16);                       // 둘째 O: 첫 O 위(16~17행)에 착지
        assertTrue(session.isLockDelayActive());

        session.moveRight();
        session.moveRight();                     // 6~7열: 아래가 비었다
        assertFalse(session.isLockDelayActive());
        assertEquals(FALL, lastStart());

        tick(session, 2);                        // 18행까지 떨어져 다시 착지
        assertTrue(session.isLockDelayActive());
        assertEquals("....OOoo..", row(session, 19));
    }

    @Test
    void 바닥_가장자리를_오가도_초기화_15회를_넘기면_다시_닿는_순간_고정한다() {
        GameSession session = started(new OBlock(), new OBlock(), new TBlock());
        tick(session, 19);                       // 첫 O: 4~5열 18~19행 고정
        tick(session, 16);                       // 둘째 O: 첫 O 위(16~17행)에 착지
        session.moveRight();                     // 5~6열: 아직 첫 O에 걸쳐 있다 (초기화 1회)

        int freshDelays = 0;
        int cycles = 0;
        while (cycles < 100 && session.isLockDelayActive()) {
            session.moveRight();                 // 6~7열: 공중 (바닥 이탈도 1회로 센다)
            int before = clock.starts.size();
            session.moveLeft();                  // 5~6열: 다시 닿는다
            if (clock.starts.size() > before && lastStart() == DELAY) freshDelays++;
            cycles++;
        }

        assertEquals(14, cycles);                // 1 + 14 = 15회를 다 쓴 뒤 다시 닿는 순간 고정
        assertEquals(13, freshDelays);           // 그 전까지만 새 유예를 받는다
        assertEquals(".....OO...", row(session, 16));
        assertEquals(".....OO...", row(session, 17));
        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void Soft_Drop은_유예_중_Lock을_앞당기지_않는다() {
        GameSession session = started(new OBlock(), new TBlock());
        tick(session, 18);
        session.moveDown();
        session.moveDown();
        assertEquals("....oo....", row(session, 19));
        assertEquals(18, session.snapshot().score()); // 실패한 Soft Drop은 점수 없음
    }

    @Test
    void Hard_Drop은_유예_없이_즉시_고정한다() {
        GameSession session = started(new OBlock(), new TBlock());
        session.hardDrop();
        assertEquals("....OO....", row(session, 19));
        assertFalse(session.isLockDelayActive());
    }

    @Test
    void 유예_중_Pause_후_Resume하면_유예를_처음부터_다시_센다() {
        GameSession session = started(new OBlock(), new TBlock());
        tick(session, 18);
        session.pause();
        session.resume();
        assertEquals(DELAY, lastStart());
        session.tick();
        assertEquals("....OO....", row(session, 19));
    }

    @Test
    void 쌓인_블록에_닿은_채로_나오면_바로_유예를_시작한다() {
        OBlock[] blocks = new OBlock[11];
        for (int i = 0; i < blocks.length; i++) blocks[i] = new OBlock();
        GameSession session = started(blocks);
        for (int k = 0; k < 9; k++) tick(session, 19 - 2 * k); // 2~19행을 O 9개로 채움
        assertTrue(session.isLockDelayActive());                 // 0~1행에 나온 10번째 O
        assertEquals(DELAY, lastStart());
    }

    @Test
    void ImmediateLockPolicy는_1차_규칙대로_하강_실패_즉시_고정한다() {
        GameSession session = started(LockDelayPolicyChoice.IMMEDIATE, new OBlock(), new TBlock());
        tick(session, 18);
        assertFalse(session.isLockDelayActive());
        session.moveDown();
        assertEquals("....OO....", row(session, 19));
        assertEquals(List.of(FALL, FALL), clock.starts);
    }
    //#endregion

    //#region 마지막 행동 플래그 (lastActionIsRotate)
    @Test
    void 회전_성공은_true_이동과_하강은_false() {
        GameSession session = started(new TBlock(), new OBlock());
        assertFalse(session.isLastActionRotate());  // Spawn 직후
        session.rotateRight();
        assertTrue(session.isLastActionRotate());
        session.moveLeft();
        assertFalse(session.isLastActionRotate());
        session.rotateRight();
        assertTrue(session.isLastActionRotate());
        session.tick();                              // 자동 낙하
        assertFalse(session.isLastActionRotate());
        session.rotateRight();
        session.moveDown();                          // Soft Drop
        assertFalse(session.isLastActionRotate());
    }

    @Test
    void 실패한_이동과_하강은_플래그를_바꾸지_않는다() {
        GameSession session = started(new TBlock(), new OBlock());
        session.rotateRight();
        tick(session, 17);                           // 바닥에 닿음 (마지막 행동: 하강)
        assertFalse(session.isLastActionRotate());
        session.rotateRight();
        assertTrue(session.isLastActionRotate());
        session.moveDown();                          // 하강 실패
        assertTrue(session.isLastActionRotate());
    }

    @Test
    void 회전으로_끝낸_블록은_Lock한_뒤에도_플래그가_남는다() {
        boolean[] seenAtLock = new boolean[1];
        GameSession[] holder = new GameSession[1];
        SpawnBoundary deferred = new SpawnBoundary() {
            @Override public void onLockResolved(LockResult result) { seenAtLock[0] = holder[0].isLastActionRotate(); }
            @Override public boolean deferSpawn() { return true; }
        };
        GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.NORMAL, sequence(new TBlock(), new OBlock()),
                clock, new RecordingListener(), deferred);
        holder[0] = session;
        session.start();
        session.rotateRight();
        tick(session, 17);
        session.rotateRight();
        session.tick();                              // 회전 성공 직후 그대로 굳었다 → T-스핀 판정 대상
        assertTrue(session.isSpawnPending());
        assertTrue(seenAtLock[0]);
        assertTrue(session.isLastActionRotate());

        session.resumeSpawn();
        assertFalse(session.isLastActionRotate());   // 새 블록은 false에서 시작
    }

    @Test
    void 내려간_Hard_Drop은_회전_플래그를_지운다() {
        GameSession session = started(new TBlock(), new OBlock());
        session.rotateRight();
        session.hardDrop();                          // 내려갔다 → false, 다음 블록 Spawn
        assertFalse(session.isLastActionRotate());
    }
    //#endregion
}
