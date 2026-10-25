package tetris.feature.item;

/**
 * 지속 효과 (점수 배율, 낙하 간격 배율). D와 S가 여기에 등록하고, 남은 블록 수는 효과마다 따로 센다 (ITS-6).
 * TODO(C 트랙): 등록·갱신·남은 블록 수 계산을 구현한다 (#68, #69). 지금은 효과가 없는 기본값만 돌려준다.
 */
public class GameModifiers {
    /** 줄 삭제 점수에 곱하는 배율 (D가 켜져 있으면 2) */
    public int scoreMultiplier() {
        return 1;
    }

    /** 낙하 간격에 곱하는 배율 (S가 켜져 있으면 1.5) */
    public double fallMultiplier() {
        return 1.0;
    }
}
