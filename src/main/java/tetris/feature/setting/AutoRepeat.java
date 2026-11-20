package tetris.feature.setting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 모던 좌우 DAS/ARR와 소프트 드롭 반복 (DAS-1). OS 자동 반복은 쓰지 않는다.
 * 처음 눌림은 호출한 쪽이 바로 처리하고, 여기에는 그 시각만 남긴다.
 * 좌우 첫 반복은 150ms, 그 다음은 33ms. 소프트 드롭은 33ms마다.
 */
public final class AutoRepeat {
    public static final int DAS_MILLIS = 150;
    public static final int ARR_MILLIS = 33;
    public static final int SOFT_DROP_MILLIS = 33;

    private final Map<GameAction, Long> nextAt = new EnumMap<>(GameAction.class);

    /** 이미 누르고 있으면 무시한다 (OS 반복 이벤트). 처음 눌림만 타이머를 건다 */
    public void press(GameAction action, long now) {
        if (action == null || !action.isRepeatable() || nextAt.containsKey(action)) return;
        nextAt.put(action, now + initialDelay(action));
    }

    public void release(GameAction action) {
        if (action != null) nextAt.remove(action);
    }

    public void clear() {
        nextAt.clear();
    }

    public boolean isHeld(GameAction action) {
        return nextAt.containsKey(action);
    }

    /**
     * now까지 밀린 반복을 순서대로 돌려준다. 한 번에 여러 간격이 지났으면 간격마다 한 번씩 넣는다.
     * 처음 눌림 자체는 포함하지 않는다.
     */
    public List<GameAction> poll(long now) {
        List<GameAction> due = new ArrayList<>();
        for (GameAction action : GameAction.values()) {
            Long at = nextAt.get(action);
            if (at == null) continue;
            int interval = interval(action);
            while (now >= at) {
                due.add(action);
                at += interval;
            }
            nextAt.put(action, at);
        }
        return due;
    }

    private static int initialDelay(GameAction action) {
        return action == GameAction.SOFT_DROP ? SOFT_DROP_MILLIS : DAS_MILLIS;
    }

    private static int interval(GameAction action) {
        return action == GameAction.SOFT_DROP ? SOFT_DROP_MILLIS : ARR_MILLIS;
    }
}
