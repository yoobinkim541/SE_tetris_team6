package tetris.ui.versus;

import java.awt.event.KeyEvent;
import java.util.*;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.PlayerId;

/** Separate in-memory versus keys; never reads or overwrites single-player settings. */
public final class BattleKeyMap {
    public record Target(PlayerId playerId, GameAction action) {
        public Target { Objects.requireNonNull(playerId); Objects.requireNonNull(action); }
    }
    private final Map<Target,Integer> keys = new LinkedHashMap<>();
    public BattleKeyMap() { this(true); }
    /** P2P: 이 PC의 참가자 한 명만 조작한다. P1 키(방향키/Space)와 공통 P/Q만 둔다 (S9-B2) */
    public static BattleKeyMap online() { return new BattleKeyMap(false); }
    private BattleKeyMap(boolean twoPlayers) {
        GameAction[] actions={GameAction.MOVE_LEFT,GameAction.MOVE_RIGHT,GameAction.SOFT_DROP,GameAction.ROTATE,GameAction.HARD_DROP};
        int[] one={KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT,KeyEvent.VK_DOWN,KeyEvent.VK_UP,KeyEvent.VK_SPACE};
        int[] two={KeyEvent.VK_A,KeyEvent.VK_D,KeyEvent.VK_S,KeyEvent.VK_W,KeyEvent.VK_F};
        for(int i=0;i<actions.length;i++) {
            keys.put(new Target(PlayerId.ONE,actions[i]),one[i]);
            if(twoPlayers) keys.put(new Target(PlayerId.TWO,actions[i]),two[i]);
        }
        keys.put(new Target(PlayerId.ONE,GameAction.PAUSE),KeyEvent.VK_P);
        keys.put(new Target(PlayerId.ONE,GameAction.QUIT),KeyEvent.VK_Q);
    }
    private Target target(PlayerId player,GameAction action) {
        return new Target(isShared(action) ? PlayerId.ONE : Objects.requireNonNull(player),action);
    }
    public static boolean isShared(GameAction action) { return action==GameAction.PAUSE || action==GameAction.QUIT; }
    public boolean isOnline() { return keys.keySet().stream().noneMatch(t -> t.playerId()==PlayerId.TWO); }
    public int keyOf(PlayerId player,GameAction action) { return keys.get(target(player,action)); }
    /** Reject conflicts across both players and shared actions without swapping keys. */
    public boolean assign(PlayerId player,GameAction action,int code) {
        Target target=target(player,action);
        if(!KeyMap.isAssignable(code)) return false;
        if(keys.get(target)==code) return true;
        if(keys.containsValue(code)) return false;
        keys.put(target,code); return true;
    }
    public Map<Target,Integer> bindings() { return Collections.unmodifiableMap(new LinkedHashMap<>(keys)); }

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
            + " / 즉시 내리기 " + keyName(player, GameAction.HARD_DROP);
    }
    private String keyName(PlayerId player, GameAction action) {
        return keyLabel(keyOf(player, action));
    }

    /** 화면에 쓸 키 이름. 방향키는 화살표로, 나머지는 OS 키 이름 (S12-B1: 영어 Left/Right 대신) */
    public static String keyLabel(int code) {
        return switch (code) {
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            default -> KeyEvent.getKeyText(code);
        };
    }

    /** 보드 옆에 크게 쓰는 한 사람의 키 안내 (S12-B1). 한 줄이 짧아 작은 창에서도 읽힌다 */
    public List<String> playerLines(PlayerId player) {
        return List.of(
            keyName(player, GameAction.MOVE_LEFT) + " " + keyName(player, GameAction.MOVE_RIGHT) + "  이동",
            keyName(player, GameAction.ROTATE) + "  회전",
            keyName(player, GameAction.SOFT_DROP) + "  내리기",
            keyName(player, GameAction.HARD_DROP) + "  즉시 낙하");
    }

    /** 두 사람 공통 키 한 줄 */
    public String sharedLine() {
        return "일시정지 " + keyName(PlayerId.ONE, GameAction.PAUSE)
            + (isOnline() ? " · 기권 " + keyName(PlayerId.ONE, GameAction.QUIT) + " 두 번"
                          : " · 나가기 " + keyName(PlayerId.ONE, GameAction.QUIT));
    }
}
