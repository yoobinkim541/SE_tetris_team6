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
                           CardOffer offer, List<RaidEvent> events, Result result,
                           List<BossView> raceBosses, int[] raceKills, int[] raceGauges) {
    /** 솔로·협동. 레이스 전용 필드는 비어 있다 */
    public RaidSnapshot(String raidId, long revision, RaidMode mode, RaidPhase phase, BossView boss,
                        List<PlayerView> players, int kills, long elapsedMillis, int gauge,
                        CardOffer offer, List<RaidEvent> events, Result result) {
        this(raidId, revision, mode, phase, boss, players, kills, elapsedMillis, gauge, offer, events, result,
                List.of(), new int[0], new int[0]);
    }

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
        raceBosses = raceBosses == null ? List.of() : List.copyOf(raceBosses);
        raceKills = raceKills == null ? new int[0] : raceKills.clone();
        raceGauges = raceGauges == null ? new int[0] : raceGauges.clone();
        if (!raceBosses.isEmpty() && raceBosses.size() != players.size()) {
            throw new IllegalArgumentException("race bosses must match players");
        }
        if (raceKills.length != 0 && raceKills.length != players.size()) {
            throw new IllegalArgumentException("race kills must match players");
        }
        if (raceGauges.length != 0 && raceGauges.length != players.size()) {
            throw new IllegalArgumentException("race gauges must match players");
        }
        for (int value : raceGauges) {
            if (value < 0 || value > 100) throw new IllegalArgumentException("race gauge must be 0..100");
        }
    }

    /** 레이스면 그 참가자의 보스, 아니면 공유 보스 */
    public BossView bossFor(int player) {
        if (player >= 0 && player < raceBosses.size()) return raceBosses.get(player);
        return boss;
    }

    /** 레이스면 그 참가자의 필살 게이지, 아니면 공유 게이지 */
    public int gaugeFor(int player) {
        if (player >= 0 && player < raceGauges.length) return raceGauges[player];
        return gauge;
    }

    @Override
    public int[] raceKills() {
        return raceKills.clone();
    }

    @Override
    public int[] raceGauges() {
        return raceGauges.clone();
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
     * @param board            보드·NEXT·홀드·고스트 (대전과 같은 모양). 미리보기는 최대 3개
     * @param lucky            20x10. 럭키 블록 칸이면 1
     * @param effects          걸려 있는 보스 효과
     * @param reviveProgress   쓰러져 있으면 팀원이 지운 줄 수, 아니면 0
     * @param reviveRequired   부활에 필요한 줄 수
     * @param damageDealt      이번 판 누적 데미지
     * @param extendedPreviews 망원경으로 NEXT가 4개 이상이 되면 4번째부터. 3개 이하면 빈 목록.
     *                         화면은 {@code board.previews()} 뒤에 이어 붙인다
     */
    public record PlayerView(MatchSnapshot.PlayerView board, boolean alive, int[][] lucky, List<Effect> effects,
                             int reviveProgress, int reviveRequired, long damageDealt, List<int[][]> extendedPreviews) {
        /** 미리보기가 3개 이하일 때의 생성자. 기존 호출은 그대로 둔다 */
        public PlayerView(MatchSnapshot.PlayerView board, boolean alive, int[][] lucky, List<Effect> effects,
                          int reviveProgress, int reviveRequired, long damageDealt) {
            this(board, alive, lucky, effects, reviveProgress, reviveRequired, damageDealt, List.of());
        }

        public PlayerView {
            Objects.requireNonNull(board, "board");
            lucky = copy(lucky);
            effects = List.copyOf(effects);
            extendedPreviews = copyShapes(extendedPreviews);
        }

        @Override
        public int[][] lucky() {
            return copy(lucky);
        }

        @Override
        public List<int[][]> extendedPreviews() {
            return copyShapes(extendedPreviews);
        }

        private static int[][] copy(int[][] grid) {
            if (grid == null) return new int[20][10];
            int[][] out = new int[grid.length][];
            for (int r = 0; r < grid.length; r++) out[r] = grid[r].clone();
            return out;
        }

        private static List<int[][]> copyShapes(List<int[][]> shapes) {
            if (shapes == null || shapes.isEmpty()) return List.of();
            List<int[][]> out = new java.util.ArrayList<>();
            for (int[][] shape : shapes) {
                if (shape == null) throw new IllegalArgumentException("preview");
                int[][] copy = new int[shape.length][];
                for (int r = 0; r < shape.length; r++) copy[r] = shape[r].clone();
                out.add(copy);
            }
            return List.copyOf(out);
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

    /**
     * 판 결과. 솔로·협동은 처치 수 → 총 데미지 → 생존 시간.
     * 레이스는 winner가 0 또는 1이고, 무승부면 null.
     */
    public record Result(int kills, long totalDamage, long survivedMillis, Integer winner) {
        /** 솔로·협동. 승자는 없다 */
        public Result(int kills, long totalDamage, long survivedMillis) {
            this(kills, totalDamage, survivedMillis, null);
        }

        public Result {
            if (winner != null && (winner < 0 || winner > 1)) {
                throw new IllegalArgumentException("winner must be 0, 1, or null");
            }
        }
    }
}
