package tetris.feature.game;

public enum GameState {
        LOADING, // 게임 시작 전 로딩
        PLAYING, // 게임 진행
        PAUSED, // 일시정지
        GAME_OVER, // 게임 끝 (점수 계산 후 종료)
        ENDED // 게임 종료
}
