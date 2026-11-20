package tetris.ui.fx;

import tetris.component.block.Block;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.item.Cell;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchResult;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerId;

/**
 * 화면이 스냅샷 차이로 고른 효과음과 배경음.
 * 재생 장치는 {@link Sink}로 갈아 끼운다. 기본은 {@link SoundEffects#SHARED}.
 *
 * <p>배경음은 메뉴 {@code bgm_menu}, 레이드·대전 {@code bgm_raid}.
 * 싱글 플레이는 이동·착지 소리가 잦아 배경음을 멈춘다.
 * 효과음·배경음 끄기는 다음 재생부터 바로 적용된다.
 */
public final class GameSounds {
    public static final GameSounds SHARED = new GameSounds(Sink.of(SoundEffects.SHARED));

    private final Sink sink;
    private boolean sfx = true;
    private boolean bgm = true;
    private SoundId wanted;
    private GameSnapshot single;
    private boolean singleSkip;
    private int suppressLines;
    private MatchSnapshot battle;
    private String battleResultId;
    private String battleCountdownId;
    private RaidSnapshot raid;
    private long countdownAnchor = -1;
    private int countdownDigit = -1;

    public GameSounds(Sink sink) {
        if (sink == null) throw new IllegalArgumentException("sink");
        this.sink = sink;
    }

    /** 설정 화면·저장 파일의 효과음·배경음. 바꾸는 즉시 반영한다 */
    public void apply(Settings settings) {
        if (settings == null) return;
        sfx = settings.isSfxEnabled();
        bgm = settings.isBgmEnabled();
        refreshLoop();
    }

    public void setSfxEnabled(boolean enabled) {
        sfx = enabled;
    }

    public void setBgmEnabled(boolean enabled) {
        bgm = enabled;
        refreshLoop();
    }

    public void menuMove() {
        play(SoundId.MENU_MOVE);
    }

    public void menuSelect() {
        play(SoundId.MENU_SELECT);
    }

    /** 메뉴·로비·결과·설정. {@code bgm_menu} */
    public void enterMenu() {
        want(SoundId.BGM_MENU);
    }

    /** 레이드와 대전은 같은 배경음({@code bgm_raid}) */
    public void enterRaid() {
        want(SoundId.BGM_RAID);
    }

    public void enterBattle() {
        enterRaid();
    }

    /** 싱글 게임 화면. 조작음과 겹치지 않게 배경음을 끈다 */
    public void enterGame() {
        want(null);
    }

    /** 싱글 세션 리스너 앞에 붙인다. 엔진은 건드리지 않는다 */
    public GameListener listening(GameListener delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate");
        return new GameListener() {
            @Override public void onChanged(GameSnapshot snapshot) {
                onSingle(snapshot);
                delegate.onChanged(snapshot);
            }

            @Override public void onGameOver(GameSnapshot snapshot) {
                onSingleOver(snapshot);
                delegate.onGameOver(snapshot);
            }
        };
    }

    /** 싱글 보드. 줄 수 증가·CLEARING 칸으로 삭제 단수를 고른다 */
    public void onSingle(GameSnapshot next) {
        if (next == null) return;
        GameSnapshot prev = single;
        single = next;
        if (singleSkip || prev == null || next.score() < prev.score() || next.lines() < prev.lines()) {
            singleSkip = false;
            suppressLines = 0;
            return;
        }
        if (next.state() == GameState.PAUSED || prev.state() == GameState.PAUSED) return;

        if (next.state() == GameState.CLEARING && prev.state() != GameState.CLEARING) {
            soundLock(prev, next, clearingLines(next));
            return;
        }
        int fresh = freshLines(next.lines() - prev.lines());
        if (fresh > 0) playClear(fresh);

        Block before = prev.currentBlock();
        Block after = next.currentBlock();
        if (holdChanged(prev.holdBlock(), next.holdBlock())) {
            play(SoundId.HOLD);
            return;
        }
        if (before != null && after != null && before.getType() == after.getType()
                && Math.abs(before.getRow() - after.getRow()) <= 2
                && Math.abs(before.getCol() - after.getCol()) <= 2) {
            if (before.getRotation() != after.getRotation()) play(SoundId.ROTATE);
            else if (before.getCol() != after.getCol() && before.getRow() == after.getRow()) play(SoundId.MOVE);
            return;
        }
        if (before != null && (after == null || before.getType() != after.getType()
                || Math.abs(before.getRow() - after.getRow()) > 2)) {
            soundLock(prev, next, fresh);
        }
    }

    public void onSingleOver(GameSnapshot snapshot) {
        single = snapshot;
        singleSkip = true;
        suppressLines = 0;
        play(SoundId.DEFEAT);
    }

    /**
     * 대전 스냅샷. {@code focus}가 null이면 두 보드 모두 내 보드(로컬).
     * P2P는 이 PC의 참가자만 조작음·승패음을 낸다.
     */
    public void onBattle(MatchSnapshot previous, MatchSnapshot next, PlayerId focus) {
        if (next == null) return;
        if (battle != null && battle.matchId().equals(next.matchId()) && battle.revision() == next.revision()) return;
        battle = next;
        if (next.phase() == MatchPhase.COUNTDOWN && !next.matchId().equals(battleCountdownId)) {
            battleCountdownId = next.matchId();
            play(SoundId.COUNTDOWN);
        }
        if (previous != null && previous.phase() == MatchPhase.COUNTDOWN && next.phase() == MatchPhase.PLAYING) {
            play(SoundId.GO);
        }
        if (next.phase() == MatchPhase.FINISHED && !next.matchId().equals(battleResultId)) {
            battleResultId = next.matchId();
            playOutcome(next.result(), focus);
        }
        if (previous == null || next.phase() != MatchPhase.PLAYING) return;
        boolean ownOne = focus == null || focus == PlayerId.ONE;
        boolean ownTwo = focus == null || focus == PlayerId.TWO;
        int grownOne = grown(previous.one(), next.one());
        int grownTwo = grown(previous.two(), next.two());
        if (grownOne > 0 && ownOne) play(SoundId.ATTACK_RECEIVE);
        if (grownTwo > 0 && ownTwo) play(SoundId.ATTACK_RECEIVE);
        if (grownTwo > 0 && ownOne) play(SoundId.ATTACK_SEND);
        if (grownOne > 0 && ownTwo) play(SoundId.ATTACK_SEND);
        if (ownOne) diffBoard(previous.one(), next.one());
        if (ownTwo) diffBoard(previous.two(), next.two());
    }

    /** 레이드 사건·줄 삭제·카드·카운트다운·패턴 경고 */
    public void onRaid(RaidSnapshot next) {
        if (next == null) return;
        RaidSnapshot prev = raid;
        if (prev != null && prev.raidId().equals(next.raidId()) && prev.revision() == next.revision()) return;
        raid = next;
        if (!sameRaid(prev, next)) {
            countdownAnchor = -1;
            countdownDigit = -1;
        }
        if (next.phase() == RaidPhase.COUNTDOWN) {
            if (countdownAnchor < 0) countdownAnchor = System.currentTimeMillis();
            int gone = (int) ((System.currentTimeMillis() - countdownAnchor) / 1000);
            int digit = Math.max(1, 3 - gone);
            if (digit != countdownDigit) {
                countdownDigit = digit;
                play(SoundId.COUNTDOWN);
            }
        } else if (prev != null && prev.phase() == RaidPhase.COUNTDOWN && next.phase() == RaidPhase.PLAYING) {
            play(SoundId.GO);
        }
        if (next.phase() == RaidPhase.CARD_PICK && newOffer(prev, next)) play(SoundId.CARD_FLIP);
        if (prev != null && prev.boss().piecesLeft() != 1 && next.boss().piecesLeft() == 1) {
            play(SoundId.BOSS_ATTACK_WARN);
        }
        for (RaidEvent event : next.events()) play(SoundEffects.forRaidEvent(event));
        if (prev == null) return;
        int n = Math.min(prev.players().size(), next.players().size());
        for (int i = 0; i < n; i++) {
            MatchSnapshot.PlayerView before = prev.players().get(i).board();
            MatchSnapshot.PlayerView after = next.players().get(i).board();
            int cleared = fullRows(before.cells()) - fullRows(after.cells());
            if (cleared > 0 && after.score() > before.score()) playClear(Math.min(4, cleared));
        }
    }

    private void soundLock(GameSnapshot prev, GameSnapshot next, int lines) {
        if (prev.ghostBlock() != null) play(SoundId.HARDDROP);
        play(SoundId.LOCK);
        if (lines > 0) playClear(lines);
        if (next.lastTSpin().isTSpin()) play(SoundId.TSPIN);
        suppressLines = Math.max(suppressLines, lines);
    }

    private int freshLines(int delta) {
        if (delta <= 0) return 0;
        int consumed = Math.min(suppressLines, delta);
        suppressLines -= consumed;
        return delta - consumed;
    }

    private void diffBoard(MatchSnapshot.PlayerView prev, MatchSnapshot.PlayerView next) {
        int cleared = fullRows(prev.cells()) - fullRows(next.cells());
        if (cleared > 0 && next.score() > prev.score()) playClear(Math.min(4, cleared));
        if (!sameShape(prev.hold(), next.hold())) {
            play(SoundId.HOLD);
            return;
        }
        Ghost before = Ghost.of(prev.ghost());
        Ghost after = Ghost.of(next.ghost());
        boolean spawned = !sameShape(prev.next(), next.next());
        int rise = before == null ? 0 : before.rise(prev.cells());
        if (before != null && after == null) {
            play(SoundId.LOCK);
            if (rise >= 1) play(SoundId.HARDDROP);
            return;
        }
        if (spawned && before != null) {
            if (rise >= 1) play(SoundId.HARDDROP);
            play(SoundId.LOCK);
            return;
        }
        if (before != null && after != null && !before.sameShape(after)) play(SoundId.ROTATE);
        else if (before != null && after != null && before.minCol != after.minCol) play(SoundId.MOVE);
    }

    private void playOutcome(MatchResult result, PlayerId focus) {
        if (result == null || result.isDraw()) return;
        if (focus == null) {
            play(SoundId.VICTORY);
            play(SoundId.DEFEAT);
            return;
        }
        play(result.winner() == focus ? SoundId.VICTORY : SoundId.DEFEAT);
    }

    private void playClear(int lines) {
        play(switch (lines) {
            case 1 -> SoundId.CLEAR1;
            case 2 -> SoundId.CLEAR2;
            case 3 -> SoundId.CLEAR3;
            case 4 -> SoundId.TETRIS;
            default -> null;
        });
    }

    private void play(SoundId id) {
        if (!sfx || id == null || id.loops()) return;
        sink.play(id);
    }

    private void want(SoundId id) {
        wanted = id;
        refreshLoop();
    }

    private void refreshLoop() {
        if (!bgm || wanted == null) {
            sink.stopLoop();
            return;
        }
        if (sink.looping() != wanted) sink.loop(wanted);
    }

    private static boolean sameRaid(RaidSnapshot prev, RaidSnapshot next) {
        return prev != null && prev.raidId().equals(next.raidId());
    }

    private static boolean newOffer(RaidSnapshot prev, RaidSnapshot next) {
        if (prev == null || prev.phase() != RaidPhase.CARD_PICK || prev.offer() == null) return true;
        return prev.offer().chooser() != next.offer().chooser()
                || !prev.offer().cards().equals(next.offer().cards());
    }

    private static boolean holdChanged(Block before, Block after) {
        if (before == null || after == null) return before != after;
        return before.getType() != after.getType();
    }

    private static int grown(MatchSnapshot.PlayerView prev, MatchSnapshot.PlayerView next) {
        return Math.max(0, next.pendingRows().size() - prev.pendingRows().size());
    }

    static int clearingLines(GameSnapshot snapshot) {
        int[] masks = new int[32];
        int lines = 0;
        for (Cell cell : snapshot.clearingCells()) {
            int row = cell.row();
            int col = cell.col();
            if (row < 0 || row >= masks.length || col < 0 || col >= 10) continue;
            masks[row] |= 1 << col;
        }
        int full = (1 << 10) - 1;
        for (int mask : masks) if (mask == full) lines++;
        return lines;
    }

    static int fullRows(int[][] cells) {
        int lines = 0;
        if (cells == null) return 0;
        for (int[] row : cells) {
            if (row == null) continue;
            boolean full = row.length > 0;
            for (int value : row) if (value == 0) full = false;
            if (full) lines++;
        }
        return lines;
    }

    private static boolean sameShape(int[][] left, int[][] right) {
        if (left == right) return true;
        if (left == null || right == null || left.length != right.length) return false;
        for (int r = 0; r < left.length; r++) {
            if (left[r] == null || right[r] == null || left[r].length != right[r].length) return false;
            for (int c = 0; c < left[r].length; c++) {
                if ((left[r][c] == 0) != (right[r][c] == 0)) return false;
            }
        }
        return true;
    }

    /** 착지 미리보기의 상대 모양. 이동·회전·하드 드롭을 보드 배열에서 가른다 */
    private static final class Ghost {
        final int minRow;
        final int minCol;
        final int[][] pattern;

        Ghost(int minRow, int minCol, int[][] pattern) {
            this.minRow = minRow;
            this.minCol = minCol;
            this.pattern = pattern;
        }

        static Ghost of(int[][] ghost) {
            if (ghost == null) return null;
            int minR = Integer.MAX_VALUE;
            int minC = Integer.MAX_VALUE;
            int maxR = -1;
            int maxC = -1;
            for (int r = 0; r < ghost.length; r++) {
                for (int c = 0; c < ghost[r].length; c++) {
                    if (ghost[r][c] == 0) continue;
                    minR = Math.min(minR, r);
                    minC = Math.min(minC, c);
                    maxR = Math.max(maxR, r);
                    maxC = Math.max(maxC, c);
                }
            }
            if (maxR < 0) return null;
            int[][] pattern = new int[maxR - minR + 1][maxC - minC + 1];
            for (int r = 0; r < pattern.length; r++) {
                for (int c = 0; c < pattern[r].length; c++) pattern[r][c] = ghost[minR + r][minC + c];
            }
            return new Ghost(minR, minC, pattern);
        }

        boolean sameShape(Ghost other) {
            return GameSounds.sameShape(pattern, other.pattern);
        }

        int rise(int[][] cells) {
            int best = 0;
            for (int up = 0; up <= minRow; up++) {
                if (covers(cells, minRow - up, minCol)) best = up;
            }
            return best;
        }

        private boolean covers(int[][] cells, int row, int col) {
            for (int r = 0; r < pattern.length; r++) {
                for (int c = 0; c < pattern[r].length; c++) {
                    if (pattern[r][c] == 0) continue;
                    int rr = row + r;
                    int cc = col + c;
                    if (rr < 0 || cc < 0 || rr >= cells.length || cc >= cells[rr].length) return false;
                    if (cells[rr][cc] == 0) return false;
                }
            }
            return true;
        }
    }

    /** 테스트가 녹음하는 재생기. 실제 장치는 열지 않는다 */
    public interface Sink {
        void play(SoundId id);

        void loop(SoundId id);

        void stopLoop();

        SoundId looping();

        static Sink of(SoundEffects effects) {
            return new Sink() {
                @Override public void play(SoundId id) { effects.play(id); }
                @Override public void loop(SoundId id) { effects.loop(id); }
                @Override public void stopLoop() { effects.stopLoop(); }
                @Override public SoundId looping() { return effects.looping(); }
            };
        }
    }
}
