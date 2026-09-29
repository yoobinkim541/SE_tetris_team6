package tetris.feature.save;

import java.util.List;

/** 로드 결과 + 사용자에게 보여줄 경고 목록 (명세 12.2) */
public record LoadResult<T>(T value, List<String> warnings) {
}
