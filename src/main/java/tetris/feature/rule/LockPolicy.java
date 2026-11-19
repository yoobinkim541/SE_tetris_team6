package tetris.feature.rule;

/**
 * Lock 정책 (LCK-5). 착지 후 Lock까지 얼마나 기다릴지, 그 유예를 이동·회전으로 몇 번까지 다시 셀 수 있는지 정한다.
 * 1차: {@link ImmediateLockPolicy}(하강 실패 즉시 Lock). 현재 기본: {@link LockDelayPolicy}(0.5초, 15회).
 */
public interface LockPolicy {
    /** 착지 후 Lock까지 기다리는 시간(ms). 0이면 하강 시도가 실패하는 순간 Lock한다 */
    int lockDelayMillis();

    /** 한 블록이 착지 유예 중 이동·회전 성공으로 타이머를 다시 셀 수 있는 최대 횟수 */
    int maxResets();

    default boolean hasDelay() {
        return lockDelayMillis() > 0;
    }
}
