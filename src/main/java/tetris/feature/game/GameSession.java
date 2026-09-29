package tetris.feature.game;

import tetris.component.block.Block;
import tetris.feature.data.Board;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.LevelPolicy;
import tetris.feature.rule.LockPolicy;
import tetris.feature.rule.NextBlockQueue;
import tetris.feature.rule.RotationPolicy;
import tetris.feature.rule.ScoringPolicy;

/**
 * 게임 한 판의 흐름과 상태기계 (명세 4, 8장). Swing EDT 단일 스레드에서만 사용한다.
 * 기존 move/*, drop/*, clear/*, playsetting/*, timer/* 클래스의 역할을 이 클래스의 메서드로 흡수한다.
 */
public class GameSession {
    Board board;              // 착지한 블록과 보드 규칙
    Block currentBlock;       // 현재 움직이는 블록
    NextBlockQueue nextQueue; // Next 큐
    GameState state;          // 현재 게임 상태
    long score;               // 현재 점수
    int lines;                // 누적 삭제 줄 (표시용)

    // 정책·협력 객체 (생성자로 주입)
    RotationPolicy rotationPolicy;
    LockPolicy lockPolicy;
    LevelPolicy levelPolicy;
    ScoringPolicy scoringPolicy;
    GameClock clock;
    GameListener listener;

    public GameSession(BlockGenerator generator, GameClock clock, GameListener listener) {
        // 기본 정책(Basic 회전, Immediate Lock, LevelPolicy, ScoringPolicy)으로 구성
    }

    // ── 수명주기 ─────────────────────────────
    public void start() {
        // LOADING -> 초기화 -> 첫 Spawn -> PLAYING (PIPE-4)
    }

    public void restart() {
        // PAUSED -> LOADING -> 새 세션 (RST-1). 점수는 저장하지 않음
    }

    public void end() {
        // -> ENDED. 타이머 정지, 세션 폐기
    }

    // ── 조작 (PLAYING에서만 유효, 실패는 무시) ──
    public void moveLeft() {
        // 왼쪽 1칸. 불가하면 무시, 점수 0
    }

    public void moveRight() {
        // 오른쪽 1칸. 불가하면 무시, 점수 0
    }

    public void moveDown() {
        // Soft Drop: 1칸 하강 시도 (성공 +Level, 실패 Lock)
    }

    public void rotateRight() {
        // 시계방향 회전 (RotationPolicy). 실패는 무시
    }

    public void hardDrop() {
        // board.getDropDistance(currentBlock)만큼 이동 -> +칸수 x Level -> 즉시 Lock
    }

    public void tick() {
        // 자동 낙하 1회 = 하강 시도. 테스트는 타이머 없이 직접 호출
    }

    // ── Pause / Quit ─────────────────────────
    public void pause() {
        // PLAYING -> PAUSED, 타이머 정지
    }

    public void resume() {
        // PAUSED -> PLAYING, 타이머를 전체 간격으로 재시작 (TMR-3)
    }

    public void togglePause() {
        // Pause 키: PLAYING이면 pause(), PAUSED면 resume(). 그 외 상태는 무시 (KEY: Pause 키는 최초 눌림만)
    }

    public void requestQuit() {
        // Quit 키: 확인창 동안 게임 정지 (QIT-1)
    }

    public void confirmQuit() {
        // Quit 확인 Yes: 세션 폐기 -> ENDED (점수는 저장하지 않음)
    }

    public void cancelQuit() {
        // Quit 확인 No: 이전 상태로 복귀
    }

    public void onFocusLost() {
        // 창 포커스 이탈: PLAYING이면 자동 Pause (PAU-3)
    }

    // ── 조회 ─────────────────────────────────
    public GameState getState() {
        throw new UnsupportedOperationException("TODO");
    }

    public GameSnapshot snapshot() {
        throw new UnsupportedOperationException("TODO");
    }

    // ── 명세 4장 파이프라인 (내부) ────────────
    private boolean tryDescend() {
        // 한 칸 하강 시도. 성공하면 score += softDropScore(level) 후 true, 실패 false
        throw new UnsupportedOperationException("TODO");
    }

    private void lockCurrentBlock() {
        // L1~L5: 보드에 기록 -> 완성 행 삭제 -> 보너스 -> lineCounter/Level 갱신 -> spawnNextBlock()
    }

    private void spawnNextBlock() {
        // S1~S4: 큐에서 꺼내 board.placeAtSpawn(block) -> false(충돌)이면 gameOver() -> blockCounter/Level 갱신 -> 타이머 재시작
    }

    private void gameOver() {
        // GAME_OVER 전환, 타이머 정지, listener.onGameOver
    }
}
