package tetris.feature.save;

import tetris.feature.score.Scoreboard;

/** scoreboard.json 입출력 (PER-1~5). 무효 항목은 버리고, 손상 파일은 *.corrupt로 옮기는 것을 권장. */
public class ScoreboardRepository {
    public LoadResult<Scoreboard> load() {
        throw new UnsupportedOperationException("TODO");
    }

    /** 원자적 저장. 실패하면 false */
    public boolean save(Scoreboard scoreboard) {
        throw new UnsupportedOperationException("TODO");
    }
}
