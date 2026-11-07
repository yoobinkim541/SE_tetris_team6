package tetris.feature.versus.api;

/**
 * Lock·줄 삭제가 끝나고 다음 블록이 Spawn되기 직전의 경계 (R3-ATTACK: "현재 블록을 처리하고 나면,
 * 다음 블록이 생성되기 전에 보드에 추가"). 대전은 여기서 대기 중인 공격 줄을 보드 아래에 넣는다.
 * 싱글 플레이는 {@link #NONE}을 써서 기존 동작이 바뀌지 않는다.
 */
@FunctionalInterface
public interface PreSpawnHook {
    PreSpawnHook NONE = () -> { };

    void beforeSpawn();
}
