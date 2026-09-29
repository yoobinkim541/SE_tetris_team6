package tetris.ui;

import javax.swing.JPanel;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSnapshot;
import tetris.feature.setting.Settings;

/** 게임 화면: Board, Next, Score, Level, GameState, Key Guide. 기존 BoardScreen 대체. */
public class GamePanel extends JPanel implements GameListener {
    public GamePanel(Settings settings) {
        // 셀 크기·팔레트·Key Guide를 설정에서 읽음
    }

    @Override
    public void onChanged(GameSnapshot snapshot) {
        // 스냅샷을 보관하고 repaint
    }

    @Override
    public void onGameOver(GameSnapshot snapshot) {
        // 마지막 상태 유지, GameOver 화면 전환 요청
    }

    /** 설정 변경 시 셀 크기·색맹 모드 다시 적용 */
    public void applySettings(Settings settings) {
        // 렌더링 계층만 갱신
    }
}
