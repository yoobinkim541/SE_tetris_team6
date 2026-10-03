package tetris.feature.save;

import tetris.feature.score.Scoreboard;

/** 스코어보드 저장소. 구현이 JSON 파일인지는 쓰는 쪽이 알 필요가 없다 */
public interface ScoreboardRepository {
    /** 없음/손상/일부 무효 시 가능한 만큼 살린 스코어보드를 돌려주고 warnings에 사유를 담는다 */
    LoadResult<Scoreboard> load();

    /** 실패하면 false */
    boolean save(Scoreboard scoreboard);
}
