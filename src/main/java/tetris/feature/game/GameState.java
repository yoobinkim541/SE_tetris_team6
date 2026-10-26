package tetris.feature.game;

public enum GameState {
    LOADING,   // 게임 시작 전 로딩
    PLAYING,   // 게임 진행
    CLEARING,  // Lock 뒤 지워질 줄을 흰색으로 보여주는 150 ms (ANI-2). 입력과 자동 낙하는 무시한다
    PAUSED,    // 일시정지
    GAME_OVER, // 게임 끝 (점수 계산 후 종료)
    ENDED      // 게임 종료
}
