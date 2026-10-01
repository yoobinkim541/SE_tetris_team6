package tetris.feature.rule;

/** 하강 실패 = 즉시 Lock (LCK-2) */
public class ImmediateLockPolicy implements LockPolicy {
    @Override
    public boolean shouldLock(boolean descentFailed) {
        return descentFailed;
    }
}
