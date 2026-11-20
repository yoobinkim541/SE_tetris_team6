package tetris.ui.versus;

import java.awt.event.KeyEvent;
import java.util.*;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.PlayerId;

/** Separate in-memory versus keys; never reads or overwrites single-player settings. */
public final class BattleKeyMap {
    /** location이 KEY_LOCATION_UNKNOWN이면 왼쪽·오른쪽을 가리지 않는다 */
    public record Binding(int code, int location) {
        public Binding {
            if (location != KeyEvent.KEY_LOCATION_UNKNOWN
                    && location != KeyEvent.KEY_LOCATION_STANDARD
                    && location != KeyEvent.KEY_LOCATION_LEFT
                    && location != KeyEvent.KEY_LOCATION_RIGHT
                    && location != KeyEvent.KEY_LOCATION_NUMPAD) {
                throw new IllegalArgumentException("location");
            }
        }

        public static Binding any(int code) {
            return new Binding(code, KeyEvent.KEY_LOCATION_UNKNOWN);
        }

        public static Binding right(int code) {
            return new Binding(code, KeyEvent.KEY_LOCATION_RIGHT);
        }
    }

    public record Target(PlayerId playerId, GameAction action) {
        public Target { Objects.requireNonNull(playerId); Objects.requireNonNull(action); }
    }
    private final Map<Target, Binding> keys = new LinkedHashMap<>();
    public BattleKeyMap() { this(true); }
    /** P2P: 이 PC의 참가자 한 명만 조작한다. 방향키와 오른쪽 Ctrl/Shift, 공통 P/Esc */
    public static BattleKeyMap online() { return new BattleKeyMap(false); }
    private BattleKeyMap(boolean twoPlayers) {
        // 로컬: 왼쪽 보드(P1)는 WASD 쪽, 오른쪽 보드(P2)는 방향키. P2P는 방향키만
        put(PlayerId.ONE, GameAction.MOVE_LEFT, twoPlayers ? KeyEvent.VK_A : KeyEvent.VK_LEFT);
        put(PlayerId.ONE, GameAction.MOVE_RIGHT, twoPlayers ? KeyEvent.VK_D : KeyEvent.VK_RIGHT);
        put(PlayerId.ONE, GameAction.SOFT_DROP, twoPlayers ? KeyEvent.VK_S : KeyEvent.VK_DOWN);
        put(PlayerId.ONE, GameAction.ROTATE, twoPlayers ? KeyEvent.VK_W : KeyEvent.VK_UP);
        put(PlayerId.ONE, GameAction.ROTATE_CCW, twoPlayers ? Binding.any(KeyEvent.VK_E) : Binding.right(KeyEvent.VK_CONTROL));
        put(PlayerId.ONE, GameAction.HARD_DROP, twoPlayers ? KeyEvent.VK_F : KeyEvent.VK_SPACE);
        put(PlayerId.ONE, GameAction.HOLD, twoPlayers ? Binding.any(KeyEvent.VK_G) : Binding.right(KeyEvent.VK_SHIFT));
        if (twoPlayers) {
            put(PlayerId.TWO, GameAction.MOVE_LEFT, KeyEvent.VK_LEFT);
            put(PlayerId.TWO, GameAction.MOVE_RIGHT, KeyEvent.VK_RIGHT);
            put(PlayerId.TWO, GameAction.SOFT_DROP, KeyEvent.VK_DOWN);
            put(PlayerId.TWO, GameAction.ROTATE, KeyEvent.VK_UP);
            put(PlayerId.TWO, GameAction.ROTATE_CCW, Binding.right(KeyEvent.VK_CONTROL));
            put(PlayerId.TWO, GameAction.HARD_DROP, KeyEvent.VK_SPACE);
            put(PlayerId.TWO, GameAction.HOLD, Binding.right(KeyEvent.VK_SHIFT));
        }
        keys.put(new Target(PlayerId.ONE, GameAction.PAUSE), Binding.any(KeyEvent.VK_P));
        keys.put(new Target(PlayerId.ONE, GameAction.QUIT), Binding.any(KeyEvent.VK_ESCAPE));
    }
    private void put(PlayerId player, GameAction action, int code) {
        put(player, action, Binding.any(code));
    }
    private void put(PlayerId player, GameAction action, Binding binding) {
        keys.put(new Target(player, action), binding);
    }
    private Target target(PlayerId player, GameAction action) {
        return new Target(isShared(action) ? PlayerId.ONE : Objects.requireNonNull(player), action);
    }
    public static boolean isShared(GameAction action) { return action == GameAction.PAUSE || action == GameAction.QUIT; }
    public boolean isOnline() { return keys.keySet().stream().noneMatch(t -> t.playerId() == PlayerId.TWO); }
    public int keyOf(PlayerId player, GameAction action) { return binding(player, action).code(); }
    public Binding binding(PlayerId player, GameAction action) { return keys.get(target(player, action)); }
    /** 오른쪽 Ctrl/Shift처럼 위치가 정해진 키인지 */
    public boolean needsLocation(PlayerId player, GameAction action) {
        return binding(player, action).location() != KeyEvent.KEY_LOCATION_UNKNOWN;
    }
    /** Reject conflicts across both players and shared actions without swapping keys. */
    public boolean assign(PlayerId player, GameAction action, int code) {
        Target target = target(player, action);
        if (!KeyMap.isAssignable(code)) return false;
        if (keys.get(target).code() == code && keys.get(target).location() == KeyEvent.KEY_LOCATION_UNKNOWN) return true;
        if (keys.values().stream().anyMatch(binding -> binding.code() == code)) return false;
        keys.put(target, Binding.any(code));
        return true;
    }

    public Map<Target, Integer> bindings() {
        Map<Target, Integer> codes = new LinkedHashMap<>();
        keys.forEach((target, binding) -> codes.put(target, binding.code()));
        return Collections.unmodifiableMap(codes);
    }

    public Map<Target, Binding> located() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    /** 키 코드와 위치(왼쪽/오른쪽 Ctrl·Shift)가 이 바인딩과 맞는지 */
    public static boolean matches(Binding binding, KeyEvent event) {
        if (binding == null || event == null || event.getKeyCode() != binding.code()) return false;
        if (binding.location() == KeyEvent.KEY_LOCATION_UNKNOWN) return true;
        return event.getKeyLocation() == binding.location();
    }

    /** Display the actual current bindings, including remapped shared actions. */
    public String guide() {
        if (isOnline()) return playerGuide(PlayerId.ONE, "나")
            + "\n공통: 일시정지 " + keyName(PlayerId.ONE, GameAction.PAUSE)
            + " / 기권 " + keyName(PlayerId.ONE, GameAction.QUIT) + " 두 번";
        return playerGuide(PlayerId.ONE, "P1") + "\n" + playerGuide(PlayerId.TWO, "P2")
            + "\n공통: 일시정지 " + keyName(PlayerId.ONE, GameAction.PAUSE)
            + " / 나가기 " + keyName(PlayerId.ONE, GameAction.QUIT);
    }
    private String playerGuide(PlayerId player, String label) {
        return label + ": 왼쪽 " + keyName(player, GameAction.MOVE_LEFT)
            + " / 오른쪽 " + keyName(player, GameAction.MOVE_RIGHT)
            + " / 내리기 " + keyName(player, GameAction.SOFT_DROP)
            + " / 회전 " + keyName(player, GameAction.ROTATE)
            + " / 반시계 " + keyName(player, GameAction.ROTATE_CCW)
            + " / 즉시 내리기 " + keyName(player, GameAction.HARD_DROP)
            + " / 홀드 " + keyName(player, GameAction.HOLD);
    }
    private String keyName(PlayerId player, GameAction action) {
        return keyLabel(binding(player, action));
    }

    /** 화면에 쓸 키 이름. 방향키는 화살표로, 오른쪽 Ctrl/Shift는 위치를 붙인다 */
    public static String keyLabel(int code) {
        return keyLabel(Binding.any(code));
    }

    public static String keyLabel(Binding binding) {
        String name = switch (binding.code()) {
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_ESCAPE -> "Esc";
            default -> KeyEvent.getKeyText(binding.code());
        };
        if (binding.location() == KeyEvent.KEY_LOCATION_RIGHT) return "오른쪽 " + name;
        return name;
    }

    /** 보드 옆에 크게 쓰는 한 사람의 키 안내. 한 줄이 짧아 작은 창에서도 읽힌다 */
    public List<String> playerLines(PlayerId player) {
        return List.of(
            keyName(player, GameAction.MOVE_LEFT) + " " + keyName(player, GameAction.MOVE_RIGHT) + "  이동",
            keyName(player, GameAction.ROTATE) + "  회전",
            keyName(player, GameAction.ROTATE_CCW) + "  반시계",
            keyName(player, GameAction.SOFT_DROP) + "  내리기",
            keyName(player, GameAction.HARD_DROP) + "  즉시 낙하",
            keyName(player, GameAction.HOLD) + "  홀드");
    }

    /** 두 사람 공통 키 한 줄 */
    public String sharedLine() {
        return "일시정지 " + keyName(PlayerId.ONE, GameAction.PAUSE)
            + (isOnline() ? " · 기권 " + keyName(PlayerId.ONE, GameAction.QUIT) + " 두 번"
                          : " · 나가기 " + keyName(PlayerId.ONE, GameAction.QUIT));
    }
}
