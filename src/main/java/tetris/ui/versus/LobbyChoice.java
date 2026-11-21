package tetris.ui.versus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 최근 IP·모드의 ◀ 값 ▶. 콤보박스 대신 값만 가진다 */
public final class LobbyChoice {
    private final List<Object> items = new ArrayList<>();
    private final Consumer<Object> onSelect;
    private int selected = -1;
    private boolean enabled = true;
    private boolean adjusting;

    public LobbyChoice(Consumer<Object> onSelect) {
        this.onSelect = onSelect;
    }

    public void addItem(Object item) {
        items.add(item);
    }

    public void removeAllItems() {
        items.clear();
        selected = -1;
    }

    public int getItemCount() {
        return items.size();
    }

    public int getSelectedIndex() {
        return selected;
    }

    public Object getSelectedItem() {
        return selected < 0 || selected >= items.size() ? null : items.get(selected);
    }

    public void setSelectedItem(Object item) {
        setSelectedIndex(items.indexOf(item));
    }

    public void setSelectedIndex(int index) {
        selected = index;
        if (!adjusting && onSelect != null && getSelectedItem() != null) onSelect.accept(getSelectedItem());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void adjust(Runnable edit) {
        adjusting = true;
        try {
            edit.run();
        } finally {
            adjusting = false;
        }
    }
}
