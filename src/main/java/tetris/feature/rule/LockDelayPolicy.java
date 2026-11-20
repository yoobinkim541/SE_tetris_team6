package tetris.feature.rule;

/**
 * Lock Delay (LCK-6). 바닥에 닿은 뒤 0.5초 동안 굳지 않고, 그 사이 이동·회전에 성공할 때마다
 * 타이머를 처음부터 다시 센다(블록당 최대 15회). 착지한 뒤에도 회전할 틈이 있어야 T-스핀 조작이 가능하다.
 */
public class LockDelayPolicy implements LockPolicy {
    public static final int DEFAULT_DELAY_MILLIS = 500;
    public static final int DEFAULT_MAX_RESETS = 15;

    private final int delayMillis;
    private final int maxResets;

    public LockDelayPolicy() {
        this(DEFAULT_DELAY_MILLIS, DEFAULT_MAX_RESETS);
    }

    public LockDelayPolicy(int delayMillis, int maxResets) {
        if (delayMillis <= 0) throw new IllegalArgumentException("delayMillis must be positive");
        if (maxResets < 0) throw new IllegalArgumentException("maxResets cannot be negative");
        this.delayMillis = delayMillis;
        this.maxResets = maxResets;
    }

    @Override
    public int lockDelayMillis() {
        return delayMillis;
    }

    @Override
    public int maxResets() {
        return maxResets;
    }
}
