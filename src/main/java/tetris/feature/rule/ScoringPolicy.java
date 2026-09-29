package tetris.feature.rule;

/** 점수 계산 (SCR-1~3, SCR-6). 화면·스코어보드가 계산식을 중복 구현하지 않는다. */
public class ScoringPolicy {
    /** 하강 1칸(자동·Soft Drop) 점수 = level */
    public long softDropScore(int level) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Hard Drop 점수 = cells × level */
    public long hardDropScore(int cells, int level) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 줄 삭제 보너스: 1줄 100, 2줄 300, 3줄 500, 4줄 800, 0줄 0 (Level 무관) */
    public long lineClearBonus(int lines) {
        throw new UnsupportedOperationException("TODO");
    }
}
