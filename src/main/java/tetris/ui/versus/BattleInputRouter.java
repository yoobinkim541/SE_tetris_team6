package tetris.ui.versus;

import java.awt.Window;
import java.awt.event.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.*;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.PlayerCommand;
import tetris.ui.versus.BattleKeyMap.Target;

/** One root-level router for a match; construct/use/close on the EDT. */
public final class BattleInputRouter implements AutoCloseable {
    private static final Object OWNER = new Object();
    private record Installed(Object previous, Object name) { }
    private final JRootPane root;
    private final Consumer<PlayerCommand> sink;
    private final Predicate<GameAction> accepts;
    private final Set<Target> held = new LinkedHashSet<>();
    private final Map<KeyStroke,Installed> installed = new LinkedHashMap<>();
    private long sequence;
    private boolean closed;
    private String matchId;
    private Window window;
    private final WindowFocusListener focus = new WindowAdapter() {
        @Override public void windowLostFocus(WindowEvent e) { clearPressedKeys(); }
    };
    private final HierarchyListener hierarchy = this::hierarchyChanged;
    private void hierarchyChanged(HierarchyEvent e) {
        if((e.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED|HierarchyEvent.SHOWING_CHANGED))!=0) {
            attachWindow();
            if(!root.isShowing()) clearPressedKeys();
        }
    }

    public BattleInputRouter(JRootPane root,BattleKeyMap keys,Consumer<PlayerCommand> sink,Predicate<GameAction> accepts) {
        this.root=Objects.requireNonNull(root); this.sink=Objects.requireNonNull(sink); this.accepts=Objects.requireNonNull(accepts);
        Objects.requireNonNull(keys);
        if(root.getClientProperty(OWNER)!=null) throw new IllegalStateException("root already has a versus router");
        root.putClientProperty(OWNER,this);
        root.addHierarchyListener(hierarchy); attachWindow(); rebind(keys);
    }
    private void attachWindow() {
        Window next=SwingUtilities.getWindowAncestor(root);
        if(next==window) return;
        if(window!=null) window.removeWindowFocusListener(focus);
        clearPressedKeys(); window=next;
        if(window!=null) window.addWindowFocusListener(focus);
    }
    public void rebind(BattleKeyMap keys) {
        if(closed) throw new IllegalStateException("router closed");
        Objects.requireNonNull(keys); clearPressedKeys(); uninstall();
        for(var entry: keys.bindings().entrySet()) {
            Target target=entry.getKey(); int code=entry.getValue();
            install(KeyStroke.getKeyStroke(code,0,false),() -> press(target));
            int[] flags={InputEvent.SHIFT_DOWN_MASK,InputEvent.CTRL_DOWN_MASK,InputEvent.ALT_DOWN_MASK,InputEvent.META_DOWN_MASK,InputEvent.ALT_GRAPH_DOWN_MASK};
            for(int combination=0;combination<(1<<flags.length);combination++) {
                int modifiers=0;
                for(int bit=0;bit<flags.length;bit++) if((combination & (1<<bit))!=0) modifiers|=flags[bit];
                install(KeyStroke.getKeyStroke(code,modifiers,true),() -> release(target));
            }
        }
    }
    private void install(KeyStroke stroke,Runnable command) {
        InputMap map=root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        Object name=new Object(); installed.put(stroke,new Installed(map.get(stroke),name)); map.put(stroke,name);
        root.getActionMap().put(name,new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if(!closed) command.run(); }
        });
    }
    private void press(Target target) {
        if(!accepts.test(target.action())) return;
        boolean first=held.add(target);
        if(first || target.action().isRepeatable()) send(target,true);
    }
    private void release(Target target) { if(held.remove(target)) send(target,false); }
    private void send(Target target,boolean pressed) { sink.accept(new PlayerCommand(target.playerId(),target.action(),pressed,sequence++)); }
    /** Call on menu, disconnect, result transition and focus loss; releases bypass state gating. */
    public void clearPressedKeys() {
        var previous=List.copyOf(held); held.clear();
        for(Target target:previous) send(target,false);
    }
    /** Keep shared key latches until physical release to prevent pause/quit auto-repeat. */
    public void clearGameplayKeys() {
        for (Target target : List.copyOf(held)) {
            if (!BattleKeyMap.isShared(target.action())) release(target);
        }
    }
    /** Sequence stays monotonic for this router; a new match never inherits held keys. */
    public void resetForMatch(String matchId) {
        Objects.requireNonNull(matchId);
        if(!matchId.equals(this.matchId)) { clearPressedKeys(); this.matchId=matchId; }
    }
    private void uninstall() {
        InputMap map=root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for(var entry:installed.entrySet()) {
            Installed value=entry.getValue();
            if(map.get(entry.getKey())==value.name()) {
                if(value.previous()==null) map.remove(entry.getKey()); else map.put(entry.getKey(),value.previous());
            }
            root.getActionMap().remove(value.name());
        }
        installed.clear();
    }
    @Override public void close() {
        if(closed) return;
        clearPressedKeys(); closed=true; uninstall();
        root.removeHierarchyListener(hierarchy);
        if(window!=null) window.removeWindowFocusListener(focus);
        root.putClientProperty(OWNER,null);
    }
}
