package tetris.feature.game;

/** GameSession -> UI 통지. UI는 폴링하지 않고 이 콜백으로만 화면을 갱신한다. */
public interface GameListener {
    void onChanged(GameSnapshot snapshot);

    void onGameOver(GameSnapshot snapshot);
}
