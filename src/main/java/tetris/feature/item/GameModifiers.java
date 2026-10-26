package tetris.feature.item;

import java.util.ArrayList;
import java.util.List;

/** D/S의 세션별 상태. C는 activate 메서드, A는 Lock/Spawn 수명주기 메서드를 호출한다. */
public class GameModifiers {
    private static final int DURATION = 10;
    private int doubleScoreRemaining;
    private int slowRemaining;
    private boolean doubleScoreRenewed;
    private boolean currentBlockSlow;

    /** L1 직전 한 번. 이번 Lock에서 D를 재획득했는지 구분한다. */
    public void beginLock() {
        doubleScoreRenewed = false;
    }

    /** C의 D 효과가 L2에서 호출한다. 이번 Lock부터 2배, 이후 10 Lock. 중첩 대신 갱신. */
    public void activateDoubleScore() {
        doubleScoreRemaining = DURATION;
        doubleScoreRenewed = true;
    }

    /** C의 S 효과가 L2에서 호출한다. 현재 블록은 유지하고 다음 성공한 Spawn 10개를 예약한다. */
    public void activateSlow() {
        slowRemaining = DURATION;
    }

    /** L5의 행 보너스에만 곱한다. 하강 점수와 개별 칸 삭제 점수에는 적용하지 않는다. */
    public int scoreMultiplier() {
        return doubleScoreRemaining > 0 ? 2 : 1;
    }

    /** L8에서 한 번. 이번 Lock에서 획득·갱신한 D는 차감하지 않는다. */
    public void finishLock() {
        if (!doubleScoreRenewed && doubleScoreRemaining > 0) doubleScoreRemaining--;
    }

    /** S2 배치 성공 뒤 S3에서 한 번. 실패한 Spawn·Pause·Resume은 소비하지 않는다. */
    public void onSpawn() {
        currentBlockSlow = slowRemaining > 0;
        if (currentBlockSlow) slowRemaining--;
    }

    /** 현재 Spawn 블록의 배율. 다음 onSpawn까지 변하지 않는다. */
    public double fallMultiplier() {
        return currentBlockSlow ? 1.5 : 1.0;
    }

    /** 변경 불가능한 사본. S의 remainingBlocks는 현재 블록 이후 남은 Spawn 수다. */
    public List<ActiveEffect> effects() {
        List<ActiveEffect> effects = new ArrayList<>();
        if (doubleScoreRemaining > 0)
            effects.add(new ActiveEffect(ItemType.DOUBLE_SCORE, doubleScoreRemaining));
        if (slowRemaining > 0 || currentBlockSlow)
            effects.add(new ActiveEffect(ItemType.SLOW, slowRemaining));
        return List.copyOf(effects);
    }
}
