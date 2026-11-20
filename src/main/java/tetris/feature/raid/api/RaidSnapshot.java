package tetris.feature.raid.api;

import java.util.List;
import java.util.Objects;
import tetris.feature.versus.api.MatchSnapshot;

/**
 * 레이드 화면 정보 (불변).
 *
 * @param revision     스냅샷 순번(증가)
 * @param boss         지금 보스. CARD_PICK·FINISHED 중에도 마지막 보스를 보여 준다
 * @param players      참가자 화면 정보. 솔로 1개, 협동 2개 (0=왼쪽 P1, 1=오른쪽 P2)
 * @param kills        이번 판 처치 수
 * @param elapsedMillis 시작 뒤 경과(일시정지·카드 선택 제외)
 * @param gauge        협동 게이지(솔로는 필살 게이지) 0..100
 * @param offer        CARD_PICK이면 제시된 카드, 아니면 null
 * @param events       직전 스냅샷 뒤 생긴 연출용 사건
 * @param result       FINISHED이면 결과, 아니면 null
 */
public record RaidSnapshot(String raidId, long revision, RaidMode mode, RaidPhase phase, BossView boss,
                           List<PlayerView> players, int kills, long elapsedMillis, int gauge,
                           CardOffer offer, List<RaidEvent> events, Result result) {
    public RaidSnapshot {
        Objects.requireNonNull(raidId, "raidId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(boss, "boss");
        players = List.copyOf(players);
        if (players.size() != mode.players()) throw new IllegalArgumentException("players must match mode");
        if (gauge < 0 || gauge > 100) throw new IllegalArgumentException("gauge must be 0..100");
        if ((phase == RaidPhase.CARD_PICK) != (offer != null)) {
            throw new IllegalArgumentException("offer must be present exactly in CARD_PICK");
        }
        if ((phase == RaidPhase.FINISHED) != (result != null)) {
            throw new IllegalArgumentException("result must be present exactly when FINISHED");
        }
        events = List.copyOf(events);
    }

    /**
     * 보스 정보.
     *
     * @param target     다음 패턴 대상 참가자 번호. 두 명 동시면 -1 (화염 브레스·연결 공격)
     * @param piecesLeft 대상이 조각을 몇 개 더 놓으면 패턴이 발동하는지
     * @param splitHp    젤리 킹 분열 뒤 미니 젤리 HP(참가자별). 분열 전이면 빈 배열
     * @param fleeMillis 황금 테트로가 도망가기까지 남은 시간. 다른 보스는 -1
     */
    public record BossView(BossId id, int level, int hp, int maxHp, PatternId nextPattern, int target,
                           int piecesLeft, int[] splitHp, long fleeMillis) {
        public BossView {
            Objects.requireNonNull(id, "id");
            if (level < 1) throw new IllegalArgumentException("level starts at 1");
            if (maxHp <= 0 || hp < 0 || hp > maxHp) throw new IllegalArgumentException("hp must be 0..maxHp");
            if (target < -1 || target > 1) throw new IllegalArgumentException("target must be -1..1");
            splitHp = splitHp == null ? new int[0] : splitHp.clone();
        }

        @Override
        public int[] splitHp() {
            return splitHp.clone();
        }
    }

    /**
     * 한 참가자.
     *
     * @param board          보드·NEXT·홀드·고스트 (대전과 같은 모양)
     * @param lucky          20x10. 럭키 블록 칸이면 1
     * @param effects        걸려 있는 보스 효과
     * @param reviveProgress 쓰러져 있으면 팀원이 지운 줄 수, 아니면 0
     * @param reviveRequired 부활에 필요한 줄 수
     * @param damageDealt    이번 판 누적 데미지
     */
    public record PlayerView(MatchSnapshot.PlayerView board, boolean alive, int[][] lucky, List<Effect> effects,
                             int reviveProgress, int reviveRequired, long damageDealt) {
        public PlayerView {
            Objects.requireNonNull(board, "board");
            lucky = copy(lucky);
            effects = List.copyOf(effects);
        }

        @Override
        public int[][] lucky() {
            return copy(lucky);
        }

        private static int[][] copy(int[][] grid) {
            if (grid == null) return new int[20][10];
            int[][] out = new int[grid.length][];
            for (int r = 0; r < grid.length; r++) out[r] = grid[r].clone();
            return out;
        }
    }

    /**
     * 걸려 있는 효과.
     *
     * @param remainingPieces 남은 조각 수(즉시 효과는 0)
     * @param area            구름이면 {행, 열, 높이, 너비}(보이는 20x10 기준), 아니면 빈 배열
     */
    public record Effect(PatternId pattern, int remainingPieces, int[] area) {
        public Effect {
            Objects.requireNonNull(pattern, "pattern");
            area = area == null ? new int[0] : area.clone();
        }

        @Override
        public int[] area() {
            return area.clone();
        }
    }

    /** 보상 카드 제시. chooser가 고를 차례, remainingMillis가 0이 되면 가운데 카드 */
    public record CardOffer(List<CardId> cards, int chooser, long remainingMillis) {
        public CardOffer {
            cards = List.copyOf(cards);
            if (cards.size() != 3) throw new IllegalArgumentException("three cards");
            if (chooser < 0 || chooser > 1) throw new IllegalArgumentException("chooser must be 0..1");
        }
    }

    /** 판 결과 (기록 순서: 처치 수 → 총 데미지 → 생존 시간) */
    public record Result(int kills, long totalDamage, long survivedMillis) {
    }
}
