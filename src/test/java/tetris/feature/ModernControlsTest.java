package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tetris.feature.GameTestSupport.cell;
import static tetris.feature.GameTestSupport.sequence;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.OBlock;
import tetris.component.block.TBlock;
import tetris.component.block.WeightBlock;
import tetris.feature.GameTestSupport.RecordingClock;
import tetris.feature.GameTestSupport.RecordingListener;
import tetris.feature.data.Board;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameState;
import tetris.feature.item.ItemType;
import tetris.feature.rule.SrsRotationPolicy;
import tetris.feature.rule.TSpinType;
import tetris.feature.save.JsonSettingsRepository;
import tetris.feature.save.LoadResult;
import tetris.feature.setting.AutoRepeat;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.Settings;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.MatchSnapshot.PlayerView;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.KeyBindingManager;
import tetris.ui.versus.BattleInputRouter;
import tetris.ui.versus.BattleKeyMap;

/** 모던 조작: 반시계 SRS, 홀드, DAS, 입력 버퍼, 설정, 클래식 재현 */
@Timeout(30)
public class ModernControlsTest {
    private GameSession modern(Block... blocks) {
        GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.NORMAL, sequence(blocks),
                new RecordingClock(), new RecordingListener(), ControlStyle.MODERN);
        session.start();
        return session;
    }

    private GameSession classic(Block... blocks) {
        GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.NORMAL, sequence(blocks),
                new RecordingClock(), new RecordingListener(), ControlStyle.CLASSIC);
        session.start();
        return session;
    }

    @Test
    void 왼쪽_벽의_세로_I는_반시계_킥으로_오른쪽으로_밀려난다() {
        SrsRotationPolicy srs = new SrsRotationPolicy();
        IBlock i = new IBlock();
        i.rotateClockwise();
        i.setPosition(5, -2); // 세로 막대가 보드 왼쪽 끝. 제자리 반시계는 벽 밖
        assertEquals(1, srs.tryRotateCounterClockwise(new Board(), i));
        assertEquals(0, i.getRotation());
        assertEquals(0, i.getCol());
    }

    @Test
    void 반시계로_들어간_T도_T스핀이다() throws Exception {
        GameSession session = modern(new TBlock(), new OBlock());
        session.rotateLeft();
        assertTrue(session.isLastActionRotate());
        liveBlock(session).setPosition(17, 0); // 왼쪽을 가리킴. 앞쪽 코너 두 칸은 비어 있다
        Board board = liveBoard(session);
        place(board, 17, 0);
        place(board, 19, 0);
        place(board, 17, 2);
        session.tick(); // 유예 시작
        session.tick(); // 유예가 끝나 Lock
        assertEquals(TSpinType.FULL, session.getLastTSpin());
    }

    @Test
    void 홀드는_한_번만_되고_바꾸면_스폰_위치에서_나오고_무게추는_안_된다() {
        TBlock t = new TBlock();
        t.attachItem(1, 1, ItemType.DOUBLE_SCORE);
        GameSession session = modern(t, new OBlock(), new IBlock());
        session.rotateRight();
        session.hold();
        assertNotNull(session.snapshot().holdBlock());
        assertEquals(0, session.snapshot().holdBlock().getRotation());
        assertEquals(ItemType.DOUBLE_SCORE, session.snapshot().holdBlock().itemCellAt(1, 1).orElseThrow());
        assertEquals(tetris.component.block.BlockType.O, session.snapshot().currentBlock().getType());
        session.hold();
        assertEquals(tetris.component.block.BlockType.O, session.snapshot().currentBlock().getType(), "같은 블록에서 홀드는 한 번");
        session.hardDrop();
        if (session.getState() == GameState.CLEARING) session.tick();
        assertEquals(tetris.component.block.BlockType.I, session.snapshot().currentBlock().getType());
        session.hold();
        assertEquals(tetris.component.block.BlockType.T, session.snapshot().currentBlock().getType(), "홀드 칸의 T와 바뀐다");

        GameSession weight = modern(new WeightBlock(), new OBlock());
        weight.hold();
        assertNull(weight.snapshot().holdBlock());
        assertTrue(weight.snapshot().currentBlock().isWeight());
    }

    @Test
    void DAS는_150ms_뒤_처음_반복하고_그다음은_33ms마다_OS반복은_무시한다() {
        AutoRepeat repeat = new AutoRepeat();
        repeat.press(GameAction.MOVE_LEFT, 0);
        repeat.press(GameAction.MOVE_LEFT, 10); // OS 반복
        assertTrue(repeat.poll(149).isEmpty());
        assertEquals(List.of(GameAction.MOVE_LEFT), repeat.poll(150));
        assertTrue(repeat.poll(182).isEmpty());
        assertEquals(List.of(GameAction.MOVE_LEFT), repeat.poll(183));
        repeat.press(GameAction.SOFT_DROP, 0);
        assertEquals(List.of(GameAction.SOFT_DROP), repeat.poll(33));

        AtomicLong now = new AtomicLong();
        GameSession session = modern(new OBlock(), new TBlock());
        int startCol = session.snapshot().currentBlock().getCol();
        JPanel panel = new JPanel();
        KeyBindingManager keys = new KeyBindingManager(() -> { });
        keys.bindGameKeys(panel, new tetris.feature.setting.KeyMap(), session, ControlStyle.MODERN, now::get);
        press(panel, KeyEvent.VK_LEFT);
        press(panel, KeyEvent.VK_LEFT); // OS 반복은 한 칸만
        assertEquals(startCol - 1, session.snapshot().currentBlock().getCol());
        now.set(150);
        keys.pulseRepeats();
        assertEquals(startCol - 2, session.snapshot().currentBlock().getCol());
        now.set(183);
        keys.pulseRepeats();
        assertEquals(startCol - 3, session.snapshot().currentBlock().getCol());
    }

    @Test
    void 줄_삭제_중_입력은_다음_블록에_한_번씩_적용된다() {
        Block[] blocks = new Block[12];
        for (int i = 0; i < blocks.length; i++) blocks[i] = cell();
        GameSession session = modern(blocks);
        for (int col = 0; col < 10; col++) if (col != 4) dropAt(session, col);
        session.hardDrop();
        assertEquals(GameState.CLEARING, session.getState());
        session.rotateRight();
        session.rotateLeft();
        session.hold();
        session.hardDrop();
        session.tick();
        assertNotEquals(GameState.CLEARING, session.getState());
        assertNotNull(session.snapshot().holdBlock(), "버퍼된 홀드가 다음 블록에 적용된다");
    }

    @Test
    void 설정_JSON은_조작방식과_새_키를_왕복하고_예전_파일은_기본값으로_채운다() throws Exception {
        Path file = Path.of("build", "modern-controls-settings.json");
        Settings settings = new Settings();
        settings.setControlStyle(ControlStyle.CLASSIC);
        new JsonSettingsRepository(file).save(settings);
        LoadResult<Settings> loaded = new JsonSettingsRepository(file).load();
        assertEquals(ControlStyle.CLASSIC, loaded.value().getControlStyle());
        assertEquals(KeyEvent.VK_Z, loaded.value().getKeyMap().keyOf(GameAction.ROTATE_CCW));
        assertEquals(KeyEvent.VK_C, loaded.value().getKeyMap().keyOf(GameAction.HOLD));
        assertTrue(loaded.warnings().isEmpty());

        Path legacy = Path.of("build", "modern-controls-legacy.json");
        java.nio.file.Files.writeString(legacy, "{\"screenSize\":\"SMALL\",\"keys\":{"
                + "\"MOVE_LEFT\":" + KeyEvent.VK_A + ",\"MOVE_RIGHT\":" + KeyEvent.VK_D
                + ",\"SOFT_DROP\":" + KeyEvent.VK_S + ",\"ROTATE\":" + KeyEvent.VK_W
                + ",\"HARD_DROP\":" + KeyEvent.VK_F + ",\"PAUSE\":" + KeyEvent.VK_P
                + ",\"QUIT\":" + KeyEvent.VK_Q + "}}");
        LoadResult<Settings> old = new JsonSettingsRepository(legacy).load();
        assertEquals(ControlStyle.MODERN, old.value().getControlStyle());
        assertEquals(KeyEvent.VK_A, old.value().getKeyMap().keyOf(GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_Z, old.value().getKeyMap().keyOf(GameAction.ROTATE_CCW));
        assertEquals(KeyEvent.VK_C, old.value().getKeyMap().keyOf(GameAction.HOLD));
        assertTrue(old.warnings().isEmpty());

        Path broken = Path.of("build", "modern-controls-bad-style.json");
        java.nio.file.Files.writeString(broken, "{\"controlStyle\":\"FAST\"}");
        LoadResult<Settings> invalid = new JsonSettingsRepository(broken).load();
        assertEquals(ControlStyle.MODERN, invalid.value().getControlStyle());
        assertEquals(1, invalid.warnings().size());
        settings.resetToDefaults();
        assertEquals(ControlStyle.MODERN, settings.getControlStyle());
    }

    @Test
    void 오른쪽_Ctrl만_반시계이고_왼쪽_Ctrl은_아니다() {
        BattleKeyMap keys = new BattleKeyMap();
        BattleKeyMap.Binding ccw = keys.binding(PlayerId.TWO, GameAction.ROTATE_CCW);
        assertEquals(KeyEvent.VK_CONTROL, ccw.code());
        assertEquals(KeyEvent.KEY_LOCATION_RIGHT, ccw.location());
        assertTrue(BattleKeyMap.matches(ccw, key(new JPanel(), KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_RIGHT)));
        assertFalse(BattleKeyMap.matches(ccw, key(new JPanel(), KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_LEFT)));

        javax.swing.JRootPane root = new javax.swing.JRootPane();
        List<tetris.feature.versus.api.PlayerCommand> commands = new ArrayList<>();
        try (BattleInputRouter router = new BattleInputRouter(root, keys, commands::add, action -> true)) {
            assertTrue(router.dispatchLocated(key(root, KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_RIGHT)));
            assertFalse(router.dispatchLocated(key(root, KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_LEFT)));
        }
        assertEquals(2, commands.size());
        assertEquals(PlayerId.TWO, commands.get(0).playerId());
        assertEquals(GameAction.ROTATE_CCW, commands.get(0).action());
        assertTrue(commands.get(0).pressed());
    }

    @Test
    void STATE는_홀드_고스트_넥스트3을_왕복한다() throws Exception {
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        int[][] next = {{0, 2, 0}, {2, 2, 2}, {0, 0, 0}};
        int[][] hold = {{0, 1, 0, 0}, {1, 1, 1, 1}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        int[][] ghost = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        ghost[18][3] = 2;
        ghost[18][4] = 2;
        ghost[18][5] = 2;
        List<int[][]> previews = List.of(next, new int[][] {{3, 3}, {3, 3}}, new int[][] {{0, 4, 4}, {4, 4, 0}});
        PlayerView view = new PlayerView(cells, next, 10, List.of(), new int[20][10], hold, ghost, previews);
        MatchSnapshot snapshot = new MatchSnapshot("m", 3, view, view, -1, MatchPhase.PLAYING, null);
        var message = tetris.feature.network.protocol.MatchPayloads.stateMessage(snapshot);
        MatchSnapshot back = tetris.feature.network.protocol.MatchPayloads.readState(overWire(message));
        assertEquals(3, back.one().previews().size());
        assertEquals(2, back.one().ghost()[18][4]);
        assertEquals(1, back.one().hold()[1][0]);
        assertEquals(3, back.one().previews().get(1)[0][0]);
    }

    @Test
    void 클래식은_즉시_고정이고_벽_킥이_없다() {
        RecordingClock clock = new RecordingClock();
        GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.NORMAL, sequence(new OBlock(), new TBlock()),
                clock, new RecordingListener(), ControlStyle.CLASSIC);
        session.start();
        assertNull(session.snapshot().ghostBlock());
        assertEquals(1, session.snapshot().nextBlocks().size());
        session.rotateLeft();
        assertEquals(0, session.snapshot().currentBlock().getRotation());
        while (session.snapshot().currentBlock().getRow() < 18) session.moveDown();
        int starts = clock.starts.size();
        session.moveDown();
        assertTrue(java.util.Arrays.stream(session.snapshot().cells()[19]).anyMatch(value -> value != 0),
                "바닥에 닿은 소프트 드롭이 바로 Lock한다");
        assertTrue(clock.starts.subList(starts, clock.starts.size()).stream().noneMatch(interval -> interval == 500));

        GameSession wall = classic(new IBlock(), new OBlock());
        wall.rotateRight();
        for (int i = 0; i < 8; i++) wall.moveRight();
        int rotation = wall.snapshot().currentBlock().getRotation();
        int col = wall.snapshot().currentBlock().getCol();
        wall.rotateRight();
        assertEquals(rotation, wall.snapshot().currentBlock().getRotation());
        assertEquals(col, wall.snapshot().currentBlock().getCol());
    }

    private static void dropAt(GameSession session, int col) {
        int from = session.snapshot().currentBlock().getCol();
        for (int i = from; i > col; i--) session.moveLeft();
        for (int i = from; i < col; i++) session.moveRight();
        session.hardDrop();
        if (session.getState() == GameState.CLEARING) session.tick();
    }

    private static void place(Board board, int row, int col) {
        Block mark = cell();
        mark.setPosition(row, col);
        assertTrue(board.tryPlaceBlock(mark));
    }

    private static Block liveBlock(GameSession session) throws Exception {
        Field field = GameSession.class.getDeclaredField("currentBlock");
        field.setAccessible(true);
        return (Block) field.get(session);
    }

    private static Board liveBoard(GameSession session) throws Exception {
        Field field = GameSession.class.getDeclaredField("board");
        field.setAccessible(true);
        return (Board) field.get(session);
    }

    private static void press(JPanel panel, int code) {
        var stroke = javax.swing.KeyStroke.getKeyStroke(code, 0, false);
        Object name = panel.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW).get(stroke);
        panel.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(panel, 0, "test"));
    }

    private static KeyEvent key(Component source, int code, int location) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED, location);
    }

    private static tetris.feature.network.protocol.Message overWire(tetris.feature.network.protocol.Message message)
            throws Exception {
        byte[] frame = new tetris.feature.network.protocol.FrameEncoder().encode(message);
        return new tetris.feature.network.protocol.FrameDecoder().feed(frame, 0, frame.length).get(0);
    }
}
