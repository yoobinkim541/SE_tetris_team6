package tetris.feature.game;
import tetris.feature.data.Board;
import tetris.component.block.Block;

public class GameSession {
    Board board;    // 착지한 블록과 보드 규칙
    Block currentBlock; // 현재 움직이는 블록
    Block nextBlock; // 다음 블록
    GameState state; // 현재 게임 상태
    int score; // 현재 점수
    int level; // 속도 증가 관리
 
    void start(){
        // 새 게임 시작
    }

    public void moveLeft(){
        // 왼쪽으로 블록을 이동
    }

    public void moveRight(){
        // 오른쪽으로 블록을 이동
    }

    public void moveDown(){
        // 아래로 블록을 이동
    }

    public void rotateRight(){
        // 블록을 회전 (시계방향)
    }

    void hardDrop(){
        // 블록을 바로 아래로 떨어뜨림
    }

    void tick(){
        // 게임 누적 시간 확인 및 속도 제어 및 전체 시간관리
    }

    void pause(){
        // 게임 일시정지
    }

    void resume(){
        // 게임 재시작 (정지 > 시작)
    }

    void restart(){
        // 게임 재시작 (게임 초기화 > 새 게임 시작)
    }

    void end(){
        // 게임 종료
    }

    private void lockCurrentBlock(){
        // 현재 블록을 보드에 고정
    }

    private void spawnNextBlock(){
        // 다음 블록을 현재 블록으로 만들고, 새로운 다음 블록 생성
    }

    private void updateScore(){
        // 점수 계산 후 업데이트
    }

    private void checkGameOver(){
        // 게임 오버 조건 확인
    }
}
