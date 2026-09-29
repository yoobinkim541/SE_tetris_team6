package tetris.feature.score;

import java.time.Instant;

/** 스코어보드 기록 1건 (SBD-4) */
public record ScoreEntry(String name, long score, int level, int lines, Instant recordedAt) {
}
