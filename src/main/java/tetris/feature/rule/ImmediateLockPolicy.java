package tetris.feature.rule;

/** 하강 실패 = 즉시 Lock (1차 LCK-2). 유예도 초기화도 없다 */
public class ImmediateLockPolicy implements LockPolicy {
    @Override
    public int lockDelayMillis() {
        return 0;
    }

    @Override
    public int maxResets() {
        return 0;
    }
}
