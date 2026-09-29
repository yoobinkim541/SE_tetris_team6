package tetris.feature.save;

import tetris.feature.setting.Settings;

/** settings.json 입출력 (PER-1~5). Jackson은 이 계층에서만 사용. 조작키도 여기에 포함. */
public class SettingsRepository {
    /** 없음/손상/일부 무효 시 기본값으로 대체하고 warnings에 사유를 담는다 */
    public LoadResult<Settings> load() {
        throw new UnsupportedOperationException("TODO");
    }

    /** 원자적 저장. 실패하면 false */
    public boolean save(Settings settings) {
        throw new UnsupportedOperationException("TODO");
    }
}
