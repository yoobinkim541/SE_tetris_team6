package tetris.ui;

import java.util.List;
import javax.swing.JPanel;
import tetris.feature.score.ScoreEntry;

/** 스코어보드 화면. 기존 ScoreBoardScreen 대체. */
public class ScoreBoardPanel extends JPanel {
    /** highlightIndex: 방금 등록한 기록의 순위 index, 강조 없으면 -1. 기록이 없으면 '기록 없음' 표시 */
    public void show(List<ScoreEntry> entries, int highlightIndex) {
        // 목록 표시, 게임 종료 후에는 Main Menu / Exit 선택지 표시
    }
}
