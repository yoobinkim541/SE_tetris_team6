package tetris.feature.rule;

/** Lock 정책 (LCK-5). 1차: 하강 시도 실패 시 즉시 Lock. 향후 Lock Delay 구현체 추가. */
public interface LockPolicy {
    /** 하강 시도 결과(descentFailed)를 보고 지금 Lock해야 하는지 판단 */
    boolean shouldLock(boolean descentFailed);
}
