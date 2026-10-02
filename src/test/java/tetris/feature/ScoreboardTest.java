package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.score.ScoreEntry;
import tetris.feature.score.Scoreboard;

/** SBD-2~5, NAM-1~3 (진입 조건, 동점 삽입, 10개 제한, 이름 검증 표) */
public class ScoreboardTest {
    private static ScoreEntry entry(String name, long score) {
        return new ScoreEntry(name, score, 1, 0, Instant.EPOCH);
    }

    /** 점수 100, 90, ... 10 순으로 10개가 찬 스코어보드 */
    private static Scoreboard fullBoard() {
        List<ScoreEntry> list = new ArrayList<>();
        for (int i = 10; i >= 1; i--) list.add(entry("p" + i, i * 10L));
        return new Scoreboard(list);
    }

    private static List<String> names(Scoreboard board) {
        return board.entries().stream().map(ScoreEntry::name).toList();
    }

    //#region 생성·정렬·10개 제한
    @Test
    void 불러온_기록을_점수_내림차순으로_정렬한다() {
        Scoreboard board = new Scoreboard(List.of(entry("a", 10), entry("b", 30), entry("c", 20)));

        assertEquals(List.of("b", "c", "a"), names(board));
    }

    @Test
    void 불러온_기록의_동점은_읽은_순서를_유지한다() { // SBD-3
        Scoreboard board = new Scoreboard(List.of(entry("first", 50), entry("second", 50)));

        assertEquals(List.of("first", "second"), names(board));
    }

    @Test
    void 불러온_기록이_10개를_넘으면_낮은_점수를_버린다() { // SBD-1
        List<ScoreEntry> list = new ArrayList<>();
        for (int i = 1; i <= 12; i++) list.add(entry("p" + i, i));
        Scoreboard board = new Scoreboard(list);

        assertEquals(Scoreboard.MAX_ENTRIES, board.entries().size());
        assertEquals(12, board.entries().get(0).score());
        assertEquals(3, board.entries().get(9).score());
    }

    @Test
    void null을_넘기면_예외() {
        assertThrows(IllegalArgumentException.class, () -> new Scoreboard(null));
    }

    @Test
    void entries는_수정할_수_없다() {
        Scoreboard board = new Scoreboard(List.of(entry("a", 1)));

        assertThrows(UnsupportedOperationException.class, () -> board.entries().clear());
    }
    //#endregion

    //#region 진입 조건 (SBD-2, SBD-5)
    @Test
    void 기록이_10개_미만이면_점수가_0이어도_진입한다() { // SBD-2, SBD-5
        Scoreboard board = new Scoreboard(List.of(entry("a", 100)));

        assertTrue(board.qualifies(0));
    }

    @Test
    void 기록이_10개이면_10위보다_높아야_진입한다() { // SBD-2
        Scoreboard board = fullBoard(); // 10위 = 10점

        assertTrue(board.qualifies(11));
        assertFalse(board.qualifies(10)); // 동점이면 먼저 기록된 쪽이 앞
        assertFalse(board.qualifies(9));
    }
    //#endregion

    //#region 삽입 (SBD-3)
    @Test
    void 가장_높은_점수는_0번에_들어간다() {
        Scoreboard board = fullBoard();

        assertEquals(0, board.add(entry("new", 500)));
        assertEquals("new", board.entries().get(0).name());
        assertEquals(Scoreboard.MAX_ENTRIES, board.entries().size());
        assertEquals(20, board.entries().get(9).score()); // 10위였던 10점은 밀려나 버려짐
    }

    @Test
    void 동점은_기존_기록_바로_뒤에_들어간다() { // SBD-3
        Scoreboard board = fullBoard(); // 100, 90, 80, ...

        int rank = board.add(entry("new", 90));

        assertEquals(2, rank);
        assertEquals("p9", board.entries().get(1).name());
        assertEquals("new", board.entries().get(2).name());
    }

    @Test
    void 열_개가_차_있을_때_10위와_동점이면_탈락한다() { // SBD-2, SBD-3
        Scoreboard board = fullBoard();

        assertEquals(-1, board.add(entry("new", 10)));
        assertEquals("p1", board.entries().get(9).name());
    }

    @Test
    void 열_개가_차_있을_때_10위보다_낮으면_탈락한다() {
        Scoreboard board = fullBoard();

        assertEquals(-1, board.add(entry("new", 5)));
        assertEquals(Scoreboard.MAX_ENTRIES, board.entries().size());
    }

    @Test
    void 비어_있으면_점수가_0이어도_0번에_들어간다() { // SBD-5
        Scoreboard board = new Scoreboard(List.of());

        assertEquals(0, board.add(entry("zero", 0)));
        assertEquals(1, board.entries().size());
    }

    @Test
    void 중간_점수는_해당_순위에_들어간다() {
        Scoreboard board = fullBoard(); // 100, 90, 80, 70, 60, 50, ...

        assertEquals(5, board.add(entry("mid", 55)));
        assertEquals("mid", board.entries().get(5).name());
    }

    @Test
    void null_기록은_예외() {
        assertThrows(IllegalArgumentException.class, () -> fullBoard().add(null));
    }
    //#endregion

    //#region Reset
    @Test
    void clear하면_모두_삭제된다() { // SBD-7
        Scoreboard board = fullBoard();
        board.clear();

        assertTrue(board.entries().isEmpty());
        assertTrue(board.qualifies(0));
    }
    //#endregion

    //#region 이름 규칙 (NAM-1~3)
    @Test
    void 한글_영문_숫자와_내부_공백은_허용한다() { // NAM-1
        assertTrue(Scoreboard.isValidName("홍길동"));
        assertTrue(Scoreboard.isValidName("ABC"));
        assertTrue(Scoreboard.isValidName("abc123"));
        assertTrue(Scoreboard.isValidName("A B"));
        assertTrue(Scoreboard.isValidName("ㄱㅏ")); // 한글 자모
    }

    @Test
    void 앞뒤_공백은_제거한_뒤_검사한다() { // NAM-2
        assertTrue(Scoreboard.isValidName("  홍길동  "));
        assertTrue(Scoreboard.isValidName("1234567890   ")); // trim 후 10자
    }

    @Test
    void 길이는_trim_후_1자_이상_10자_이하() { // NAM-2
        assertTrue(Scoreboard.isValidName("A"));
        assertTrue(Scoreboard.isValidName("ABCDEFGHIJ"));
        assertFalse(Scoreboard.isValidName("ABCDEFGHIJK"));
    }

    @Test
    void 빈_문자열_공백만_있는_이름_null은_금지한다() { // NAM-3
        assertFalse(Scoreboard.isValidName(""));
        assertFalse(Scoreboard.isValidName("   "));
        assertFalse(Scoreboard.isValidName(null));
    }

    @Test
    void 특수문자와_이모지는_금지한다() { // NAM-1
        assertFalse(Scoreboard.isValidName("A!"));
        assertFalse(Scoreboard.isValidName("a_b"));
        assertFalse(Scoreboard.isValidName("😀")); // 이모지
    }

    @Test
    void 같은_이름의_중복_등록은_허용한다() { // NAM-3
        Scoreboard board = new Scoreboard(List.of(entry("same", 10)));

        assertEquals(0, board.add(entry("same", 20)));
        assertEquals(2, board.entries().size());
    }
    //#endregion
}
