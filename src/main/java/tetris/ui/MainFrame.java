package tetris.ui;

import javax.swing.JFrame;
import tetris.feature.setting.ScreenSize;

/** 메인 윈도우. CardLayout으로 화면을 전환한다 (명세 9, 13장). 크기 조절 불가. */
public class MainFrame extends JFrame {
    /** 앱 화면 흐름 (GameState와 별개) */
    public enum Screen { MAIN_MENU, GAME, SETTINGS, SCOREBOARD, NAME_INPUT, GAME_OVER }

    public MainFrame() {
        // 패널 등록, setResizable(false)
    }

    /** 화면 전환. 기존 ReturnToMain 역할 = showScreen(MAIN_MENU) */
    public void showScreen(Screen screen) {
        // CardLayout 전환
    }

    /** 셀 크기 적용: pack() 후 중앙 배치. 화면에 안 맞으면 셀 크기를 자동 축소 (RND-3) */
    public void applyScreenSize(ScreenSize size) {
        // 창 크기 재계산
    }

    /** 프로그램 종료 (Main Menu '게임 종료', 창 닫기) */
    public void exitApplication() {
        // 즉시 종료
    }
}
