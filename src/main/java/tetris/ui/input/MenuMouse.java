package tetris.ui.input;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** 호버는 선택, 클릭은 Enter. 포커스는 그 패널에 남겨 방향키를 빼앗지 않는다. 버튼은 만들지 않는다 */
public final class MenuMouse {
    private MenuMouse() {
    }

    public static void install(JComponent panel, Hits hits, Consumer<String> hover, Consumer<String> click) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                move(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                move(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setCursor(Cursor.getDefaultCursor());
                if (hover != null) hover.accept(null);
            }

            private void move(MouseEvent e) {
                String id = hits.hit(e.getX(), e.getY());
                panel.setCursor(id == null
                        ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (hover != null) hover.accept(id);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                String id = hits.hit(e.getX(), e.getY());
                if (id == null) return;
                panel.requestFocusInWindow();
                if (click != null) click.accept(id);
            }
        };
        panel.addMouseListener(adapter);
        panel.addMouseMotionListener(adapter);
    }
}
