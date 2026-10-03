package tetris.feature.save;

import tetris.feature.setting.Settings;

/** 설정 저장소. 구현이 JSON 파일인지는 쓰는 쪽이 알 필요가 없다 */
public interface SettingsRepository {
    /** 없음/손상/일부 무효 시 기본값으로 대체하고 warnings에 사유를 담는다 */
    LoadResult<Settings> load();

    /** 실패하면 false */
    boolean save(Settings settings);
}
