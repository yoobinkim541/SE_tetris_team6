package tetris.feature.versus.api;

/**
 * Lock 정산과 다음 Spawn 사이의 경계 (R3-ORDER: "현재 블록을 처리하고 나면, 다음 블록이 생성되기 전에
 * 보드에 추가"). 대전은 단계별로 진행하고, 싱글은 {@link #IMMEDIATE}로 지금처럼 바로 Spawn한다.
 *
 * <pre>
 * Lock → (CLEARING 150 ms) → 점수·카운터 정산 → onLockResolved(LockResult) → [deferSpawn()이면 대기] → Spawn
 * </pre>
 * 대기 중인 세션의 currentBlock은 null이므로 MatchEngine은 그 세션에 이동·회전·Hard Drop을 전달하지 않는다.
 */
public interface SpawnBoundary {
    SpawnBoundary IMMEDIATE = new SpawnBoundary() {
        @Override
        public void onLockResolved(LockResult result) {
        }

        @Override
        public boolean deferSpawn() {
            return false;
        }
    };

    /** 삭제가 확정되면 한 번 호출. 대전 엔진은 여기서 공격을 수집한다 */
    void onLockResolved(LockResult result);

    /** true면 세션이 Spawn하지 않고 엔진이 공격 줄을 넣은 뒤 Spawn을 재개할 때까지 기다린다 */
    boolean deferSpawn();
}
