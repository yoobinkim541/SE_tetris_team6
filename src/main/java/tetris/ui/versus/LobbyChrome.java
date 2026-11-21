package tetris.ui.versus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import tetris.relay.RelayRoomMode;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.UiKit;

/**
 * 온라인 로비의 가상 좌표 배치와 그리기. 클릭 판정은 여기서 만든 사각형만 쓴다.
 * 화면이 늘어나면 {@link Space}가 같은 비율로 가운데에 맞춘다.
 */
public final class LobbyChrome {
    public enum Page { INTERNET, LAN, CREATE, CODE, WAITING }

    public record Hit(String id, Rectangle bounds, String up, String down, String left, String right, boolean text) {
    }

    public record RoomRow(String id, String name, String modeLabel, boolean privateRoom, int players) {
    }

    public record View(Page page, List<RoomRow> rooms, String selectedId, String createName, boolean createPublic,
                       String modeLabel, String codeText, String port, String address, String recentLabel,
                       boolean recentEnabled, String status, boolean error, String roomCode, String roomTitle,
                       String peerLine, String readyLine, boolean hostCanEditMode, String readyLabel, String backLabel) {
    }

    public record Scene(List<Hit> hits, Rectangle glass, String guide, Rectangle statusBar, Rectangle guideBar) {
        public Hit at(int x, int y) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                if (hits.get(i).bounds().contains(x, y)) return hits.get(i);
            }
            return null;
        }

        public Hit byId(String id) {
            for (Hit hit : hits) if (hit.id().equals(id)) return hit;
            return null;
        }
    }

    /** 그리기와 클릭이 같이 쓰는 세로 배치. 유리 패널은 내용만큼만 차지한다 */
    private record Frame(int unit, int contentX, int contentW, Rectangle glass, int buttonsY, int buttonH,
                         int tabTop, int tabH, Rectangle statusBar, Rectangle guideBar) {
    }

    /** 컴포넌트 픽셀 ↔ 가상 픽셀. 배율과 가운데 여백을 한 곳에서 계산한다 */
    public record Space(int virtualWidth, int virtualHeight, double scale, int offsetX, int offsetY) {
        public static Space of(int componentWidth, int componentHeight, int virtualWidth, int virtualHeight) {
            int vw = Math.max(1, virtualWidth);
            int vh = Math.max(1, virtualHeight);
            int cw = Math.max(1, componentWidth);
            int ch = Math.max(1, componentHeight);
            double scale = Math.min(cw / (double) vw, ch / (double) vh);
            int drawnW = (int) Math.round(vw * scale);
            int drawnH = (int) Math.round(vh * scale);
            return new Space(vw, vh, scale, (cw - drawnW) / 2, (ch - drawnH) / 2);
        }

        public Point toVirtual(int screenX, int screenY) {
            return new Point((int) Math.round((screenX - offsetX) / scale), (int) Math.round((screenY - offsetY) / scale));
        }

        public Rectangle toScreen(Rectangle virtual) {
            return new Rectangle(
                    offsetX + (int) Math.round(virtual.x * scale),
                    offsetY + (int) Math.round(virtual.y * scale),
                    Math.max(1, (int) Math.round(virtual.width * scale)),
                    Math.max(1, (int) Math.round(virtual.height * scale)));
        }

        public void apply(Graphics2D g) {
            g.translate(offsetX, offsetY);
            g.scale(scale, scale);
        }
    }

    private LobbyChrome() {
    }

    public static Scene layout(View view, int width, int height) {
        Frame frame = frame(view, width, height);
        List<Hit> hits = new ArrayList<>();
        if (view.page() != Page.WAITING) {
            addTabs(hits, frame.contentX, frame.contentW, frame.tabTop, frame.tabH, frame.unit, view.page());
        }
        switch (view.page()) {
            case INTERNET -> addInternet(hits, view, frame.glass, frame.buttonsY, frame.buttonH, frame.unit);
            case LAN -> addLan(hits, view, frame.glass, frame.buttonsY, frame.buttonH, frame.unit);
            case CREATE -> addCreate(hits, view, frame.glass, frame.buttonsY, frame.buttonH, frame.unit);
            case CODE -> addCode(hits, view, frame.glass, frame.buttonsY, frame.buttonH, frame.unit);
            case WAITING -> addWaiting(hits, view, frame.glass, frame.buttonsY, frame.buttonH, frame.unit);
        }
        String guide = switch (view.page()) {
            case INTERNET -> "위아래 선택   Enter 참가   N 새 방   R 새로고침   Esc 뒤로";
            case LAN -> "위아래 선택   Enter 입력   좌우 탭   Esc 뒤로";
            case CREATE -> "위아래 이동   좌우 공개와 모드   Enter 확인   Esc 취소";
            case CODE -> "Enter 참가   Esc 목록";
            case WAITING -> "Enter 준비   Esc 나가기";
        };
        return new Scene(List.copyOf(hits), frame.glass, guide, frame.statusBar, frame.guideBar);
    }

    private static Frame frame(View view, int width, int height) {
        int unit = Math.max(12, height / 32);
        int margin = Math.max(12, unit / 2);
        int side = unit * 4 + 8;
        int contentW = Math.min(width - side * 2, Math.min(720, Math.max(unit * 18, width * 4 / 5)));
        if (contentW < unit * 12) contentW = Math.max(unit * 12, width - margin * 2);
        int contentX = (width - contentW) / 2;
        int guideH = unit + 10;
        int statusH = unit + 8;
        int guideY = height - margin - guideH;
        int statusY = guideY - statusH - Math.max(6, unit / 3);
        Rectangle guideBar = new Rectangle((width - Math.min(contentW, width * 3 / 4)) / 2, guideY,
                Math.min(contentW, width * 3 / 4), guideH);
        int statusW = Math.min(contentW, Math.max(unit * 16, width * 2 / 3));
        Rectangle statusBar = new Rectangle((width - statusW) / 2, statusY, statusW, statusH);
        int buttonH = unit + 12;
        int tabH = unit + 10;
        int rowH = unit + 12;
        int gap = Math.max(8, unit / 3);
        int rows = switch (view.page()) {
            case INTERNET -> Math.max(4, Math.min(6, Math.max(1, view.rooms().size())));
            case CREATE, LAN -> 3;
            case CODE -> 2;
            case WAITING -> 5;
        };
        int glassH = Math.max(28, unit) + unit + rows * rowH + unit / 2;
        boolean waiting = view.page() == Page.WAITING;
        int titleH = unit + 18;
        int clusterH = titleH + gap + (waiting ? 0 : tabH + gap) + glassH + Math.max(16, unit / 2) + buttonH;
        int top = Math.max(margin, unit * 4);
        int bottom = statusY - gap;
        int extra = Math.max(0, bottom - top - clusterH);
        int clusterTop = top + extra / 2;
        int tabTop = clusterTop + titleH + gap;
        int glassTop = waiting ? tabTop : tabTop + tabH + gap;
        int buttonsY = glassTop + glassH + Math.max(16, unit / 2);
        if (buttonsY + buttonH > bottom) {
            buttonsY = bottom - buttonH;
            glassH = Math.max(unit * 4, buttonsY - gap - glassTop);
        }
        Rectangle glass = new Rectangle(contentX, glassTop, contentW, glassH);
        return new Frame(unit, contentX, contentW, glass, buttonsY, buttonH, tabTop, tabH, statusBar, guideBar);
    }

    public static void paint(Graphics2D g, View view, Scene scene, int width, int height) {
        int unit = Math.max(12, height / 32);
        paintTitle(g, scene, unit);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, scene.glass().x, scene.glass().y, scene.glass().width, scene.glass().height);
        g.setColor(new Color(8, 14, 32, 150));
        g.fillRoundRect(scene.glass().x + 8, scene.glass().y + 8, scene.glass().width - 16, scene.glass().height - 16, 18, 18);
        for (Hit hit : scene.hits()) paintHit(g, view, hit, unit);
        if (view.page() == Page.INTERNET) paintListHeader(g, scene.glass(), unit);
        if (view.page() == Page.INTERNET && view.rooms().isEmpty()) {
            g.setFont(GameTheme.text(Math.max(16, unit), false));
            g.setColor(new Color(0xC9D4E8));
            UiKit.drawInBox(g, "대기 중인 방이 없습니다", inset(scene.glass(), unit), UiKit.Align.CENTER);
        }
        if (view.page() == Page.WAITING) paintWaitingCard(g, view, scene.glass(), unit);
        if (view.page() == Page.CODE) {
            g.setFont(GameTheme.text(Math.max(20, unit + 2), true));
            g.setColor(Color.WHITE);
            UiKit.drawInBox(g, "비공개 방 코드", new Rectangle(scene.glass().x, scene.glass().y + 8, scene.glass().width, unit + 8),
                    UiKit.Align.CENTER);
        }
        paintStatus(g, view, scene.statusBar());
        paintGuide(g, scene.guide(), scene.guideBar());
    }

    private static void paintTitle(Graphics2D g, Scene scene, int unit) {
        Hit tab = scene.byId("tab-net");
        int anchor = tab == null ? scene.glass().y : tab.bounds().y;
        int titleH = unit + 18;
        Rectangle title = new Rectangle(scene.glass().x, Math.max(8, anchor - titleH - 8), scene.glass().width, titleH);
        g.setFont(GameTheme.text(Math.max(28, unit + 12), true));
        g.setColor(UiKit.CYAN);
        UiKit.drawInBox(g, "온라인 대전", title, UiKit.Align.CENTER);
    }

    private static Rectangle inset(Rectangle box, int pad) {
        int x = Math.max(8, pad);
        return new Rectangle(box.x + x, box.y + x, Math.max(1, box.width - x * 2), Math.max(1, box.height - x * 2));
    }
    private static void paintListHeader(Graphics2D g, Rectangle glass, int unit) {
        int cap = Math.max(40, unit + 12);
        Rectangle band = new Rectangle(glass.x + cap, glass.y + cap / 2 + 8, Math.max(1, glass.width - cap * 2 - 18), Math.max(16, unit - 2));
        g.setFont(GameTheme.text(Math.max(12, unit - 4), false));
        g.setColor(new Color(0x8EA0BE));
        UiKit.drawInBox(g, "이름", column(band, 0, 32), UiKit.Align.LEFT);
        UiKit.drawInBox(g, "모드", column(band, 32, 56), UiKit.Align.LEFT);
        UiKit.drawInBox(g, "공개", column(band, 56, 82), UiKit.Align.LEFT);
        UiKit.drawInBox(g, "인원", column(band, 82, 100), UiKit.Align.RIGHT);
    }

    private static Rectangle column(Rectangle band, int startPercent, int endPercent) {
        int x = band.x + band.width * startPercent / 100;
        int end = band.x + band.width * endPercent / 100;
        return new Rectangle(x, band.y, Math.max(1, end - x - 8), band.height);
    }

    private static void paintWaitingCard(Graphics2D g, View view, Rectangle glass, int unit) {
        int cap = Math.max(24, unit);
        int innerW = Math.max(1, glass.width - cap * 2);
        int y = glass.y + cap;
        g.setFont(GameTheme.text(Math.max(18, unit), true));
        g.setColor(Color.WHITE);
        UiKit.drawInBox(g, view.roomTitle() == null || view.roomTitle().isEmpty() ? "인터넷 방" : view.roomTitle(),
                new Rectangle(glass.x + cap, y, innerW, unit + 8), UiKit.Align.CENTER);
        y += unit + 10;
        g.setFont(GameTheme.digits(Math.max(32, unit + 10)));
        g.setColor(UiKit.CYAN);
        UiKit.drawInBox(g, view.roomCode() == null ? "……" : view.roomCode(),
                new Rectangle(glass.x + cap, y, innerW, unit + 16), UiKit.Align.CENTER);
        y += unit + 18;
        g.setFont(GameTheme.text(Math.max(14, unit - 2), false));
        g.setColor(new Color(0xE8E8E8));
        UiKit.drawInBox(g, view.peerLine() == null ? "" : view.peerLine(),
                new Rectangle(glass.x + cap, y, innerW, unit + 4), UiKit.Align.CENTER);
        y += unit + 6;
        UiKit.drawInBox(g, view.readyLine() == null ? "" : view.readyLine(),
                new Rectangle(glass.x + cap, y, innerW, unit + 4), UiKit.Align.CENTER);
    }

    private static void addTabs(List<Hit> hits, int contentX, int contentW, int top, int height, int unit, Page page) {
        int gap = Math.max(8, unit / 2);
        int tabW = Math.max(1, (contentW - gap) / 2);
        String down = page == Page.LAN ? "field-port" : "room-0";
        hits.add(new Hit("tab-net", new Rectangle(contentX, top, tabW, height), null, down, null, "tab-lan", false));
        hits.add(new Hit("tab-lan", new Rectangle(contentX + tabW + gap, top, tabW, height), null, down, "tab-net", null, false));
    }

    private static void addInternet(List<Hit> hits, View view, Rectangle glass, int buttonsY, int buttonH, int unit) {
        int rowH = unit + 12;
        int y = glass.y + Math.max(28, unit) + unit;
        int rows = Math.max(1, (glass.height - Math.max(28, unit) - unit - 8) / rowH);
        int shown = Math.min(view.rooms().size(), rows);
        if (view.rooms().isEmpty()) {
            link(hits, "tab-net", null, "btn-create");
            link(hits, "tab-lan", null, "btn-create");
        }
        for (int i = 0; i < shown; i++) {
            String id = "room-" + i;
            String up = i == 0 ? "tab-net" : "room-" + (i - 1);
            String down = i + 1 < shown ? "room-" + (i + 1) : "btn-create";
            hits.add(new Hit(id, new Rectangle(glass.x + 12, y, glass.width - 24, rowH - 4), up, down, null, null, false));
            y += rowH;
        }
        if (shown > 0) {
            link(hits, "tab-net", null, "room-0");
            link(hits, "tab-lan", null, "room-0");
        }
        addButtonRow(hits, List.of("btn-create", "btn-code", "btn-refresh", "btn-back"),
                shown == 0 ? "tab-net" : "room-" + (shown - 1), glass.x, buttonsY, glass.width, buttonH, unit);
    }

    private static void addLan(List<Hit> hits, View view, Rectangle glass, int buttonsY, int buttonH, int unit) {
        int fieldH = unit + 12;
        int y = glass.y + unit;
        hits.add(new Hit("field-port", field(glass, y, fieldH), "tab-lan", "field-address", null, null, true));
        y += fieldH + unit / 2;
        hits.add(new Hit("field-address", field(glass, y, fieldH), "field-port", "recent", null, null, true));
        y += fieldH + unit / 2;
        hits.add(new Hit("recent", field(glass, y, fieldH), "field-address", "btn-host", null, null, false));
        link(hits, "tab-net", null, "field-port");
        link(hits, "tab-lan", null, "field-port");
        addButtonRow(hits, List.of("btn-host", "btn-connect", "btn-lan-back"), "recent",
                glass.x, buttonsY, glass.width, buttonH, unit);
    }

    private static void addCreate(List<Hit> hits, View view, Rectangle glass, int buttonsY, int buttonH, int unit) {
        int fieldH = unit + 12;
        int y = glass.y + unit;
        hits.add(new Hit("field-name", field(glass, y, fieldH), "tab-net", "vis", null, null, true));
        y += fieldH + unit / 2;
        hits.add(new Hit("vis", field(glass, y, fieldH), "field-name", "mode", null, null, false));
        y += fieldH + unit / 2;
        hits.add(new Hit("mode", field(glass, y, fieldH), "vis", "btn-make", null, null, false));
        link(hits, "tab-net", null, "field-name");
        link(hits, "tab-lan", null, "field-name");
        addButtonRow(hits, List.of("btn-make", "btn-cancel"), "mode", glass.x, buttonsY, glass.width, buttonH, unit);
    }

    private static void addCode(List<Hit> hits, View view, Rectangle glass, int buttonsY, int buttonH, int unit) {
        int fieldH = unit + 14;
        hits.add(new Hit("field-code", field(glass, glass.y + glass.height / 3, fieldH), null, "btn-join-code", null, null, true));
        addButtonRow(hits, List.of("btn-join-code", "btn-cancel-code"), "field-code",
                glass.x, buttonsY, glass.width, buttonH, unit);
    }

    private static void addWaiting(List<Hit> hits, View view, Rectangle glass, int buttonsY, int buttonH, int unit) {
        if (view.hostCanEditMode()) {
            hits.add(new Hit("wait-mode", field(glass, glass.y + glass.height - unit * 3, unit + 12),
                    null, "btn-ready", null, null, false));
            addButtonRow(hits, List.of("btn-ready", "btn-leave"), "wait-mode", glass.x, buttonsY, glass.width, buttonH, unit);
        } else {
            addButtonRow(hits, List.of("btn-ready", "btn-leave"), null, glass.x, buttonsY, glass.width, buttonH, unit);
        }
    }

    private static Rectangle field(Rectangle glass, int y, int height) {
        int inset = 28;
        return new Rectangle(glass.x + inset, y, glass.width - inset * 2, height);
    }

    private static void addButtonRow(List<Hit> hits, List<String> ids, String up, int x, int y, int width, int height, int unit) {
        int gap = Math.max(6, unit / 3);
        int buttonW = (width - gap * (ids.size() - 1)) / ids.size();
        for (int i = 0; i < ids.size(); i++) {
            String left = i == 0 ? null : ids.get(i - 1);
            String right = i + 1 < ids.size() ? ids.get(i + 1) : null;
            hits.add(new Hit(ids.get(i), new Rectangle(x + i * (buttonW + gap), y, buttonW, height), up, null, left, right, false));
        }
    }

    private static void link(List<Hit> hits, String id, String up, String down) {
        for (int i = 0; i < hits.size(); i++) {
            Hit hit = hits.get(i);
            if (!hit.id().equals(id)) continue;
            hits.set(i, new Hit(hit.id(), hit.bounds(), up == null ? hit.up() : up, down == null ? hit.down() : down,
                    hit.left(), hit.right(), hit.text()));
            return;
        }
    }

    private static void paintHit(Graphics2D g, View view, Hit hit, int unit) {
        boolean selected = hit.id().equals(view.selectedId());
        Rectangle box = hit.bounds();
        if (hit.id().startsWith("tab-")) {
            boolean internet = hit.id().equals("tab-net");
            boolean on = (internet && view.page() != Page.LAN) || (!internet && view.page() == Page.LAN);
            UiKit.paintButton(g, on || selected, box.x, box.y, box.width, box.height, false, UiKit.pulse());
            g.setFont(GameTheme.text(Math.max(14, unit - 1), on || selected));
            g.setColor(Color.WHITE);
            UiKit.drawInAsset(g, UiKit.AssetSlot.BUTTON, box, internet ? "인터넷 방 목록" : "같은 LAN",
                    UiKit.Align.CENTER, on || selected);
            return;
        }
        if (hit.id().startsWith("room-")) {
            int index = Integer.parseInt(hit.id().substring(5));
            if (index >= view.rooms().size()) return;
            RoomRow room = view.rooms().get(index);
            UiKit.paintButton(g, selected, box.x, box.y, box.width, box.height, false, UiKit.pulse());
            Rectangle hole = UiKit.contentBox(UiKit.AssetSlot.BUTTON, box);
            g.setFont(GameTheme.text(Math.max(13, unit - 2), selected));
            g.setColor(Color.WHITE);
            UiKit.drawInBox(g, room.name(), column(hole, 0, 32), UiKit.Align.LEFT);
            g.setColor(new Color(0xC9D4E8));
            UiKit.drawInBox(g, room.modeLabel(), column(hole, 32, 56), UiKit.Align.LEFT);
            g.setColor(room.privateRoom() ? UiKit.MAGENTA : UiKit.CYAN);
            UiKit.drawIconText(g, column(hole, 56, 82), room.privateRoom() ? "◆" : "◇", room.privateRoom() ? "비공개" : "공개");
            g.setFont(GameTheme.digits(Math.max(13, unit - 2)));
            g.setColor(UiKit.CYAN);
            UiKit.drawInBox(g, room.players() + "/2", column(hole, 82, 100), UiKit.Align.RIGHT);
            return;
        }
        if (hit.id().startsWith("btn-")) {
            UiKit.paintButton(g, selected, box.x, box.y, box.width, box.height, false, UiKit.pulse());
            g.setFont(GameTheme.text(Math.max(13, unit - 2), selected));
            g.setColor(Color.WHITE);
            UiKit.drawInAsset(g, UiKit.AssetSlot.BUTTON, box, buttonLabel(hit.id(), view), UiKit.Align.CENTER, selected);
            return;
        }
        UiKit.paintGlassCapsule(g, box.x, box.y, box.width, box.height);
        g.setFont(hit.id().equals("field-port") || hit.id().equals("field-code") ? GameTheme.digits(Math.max(16, unit))
                : GameTheme.text(Math.max(14, unit - 1), false));
        g.setColor(Color.WHITE);
        String value = fieldText(hit.id(), view);
        Rectangle hole = UiKit.contentBox(UiKit.AssetSlot.NAME, box);
        if (hit.id().equals("vis") || hit.id().equals("mode") || hit.id().equals("recent") || hit.id().equals("wait-mode")) {
            UiKit.drawInBox(g, "◀", new Rectangle(hole.x, hole.y, 22, hole.height), UiKit.Align.CENTER);
            UiKit.drawInBox(g, value, new Rectangle(hole.x + 24, hole.y, Math.max(1, hole.width - 48), hole.height), UiKit.Align.CENTER);
            UiKit.drawInBox(g, "▶", new Rectangle(hole.x + hole.width - 22, hole.y, 22, hole.height), UiKit.Align.CENTER);
        } else {
            UiKit.drawInAsset(g, UiKit.AssetSlot.NAME, box, value.isEmpty() ? placeholder(hit.id()) : value, UiKit.Align.CENTER);
        }
    }

    private static void paintStatus(Graphics2D g, View view, Rectangle bar) {
        UiKit.paintGlassCapsule(g, bar.x, bar.y, bar.width, bar.height);
        g.setFont(GameTheme.text(Math.max(13, bar.height / 2), false));
        g.setColor(view.error() ? new Color(0xFF8A80) : Color.WHITE);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, bar, view.status(), UiKit.Align.CENTER);
    }

    private static void paintGuide(Graphics2D g, String guide, Rectangle bar) {
        UiKit.paintCapsule(g, bar.x, bar.y, bar.width, bar.height);
        g.setFont(GameTheme.text(Math.max(13, bar.height / 2), false));
        g.setColor(new Color(0xE8E8E8));
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, bar, guide, UiKit.Align.CENTER);
    }

    private static String buttonLabel(String id, View view) {
        return switch (id) {
            case "btn-create" -> "방 만들기";
            case "btn-code" -> "코드로 참가";
            case "btn-refresh" -> "새로고침";
            case "btn-back", "btn-lan-back", "btn-cancel", "btn-cancel-code" -> "뒤로";
            case "btn-make" -> "만들기";
            case "btn-join-code" -> "참가";
            case "btn-host" -> "호스트로 대기";
            case "btn-connect" -> "접속";
            case "btn-ready" -> view.readyLabel();
            case "btn-leave" -> view.backLabel();
            default -> "";
        };
    }

    private static String fieldText(String id, View view) {
        return switch (id) {
            case "field-name" -> view.createName();
            case "vis" -> view.createPublic() ? "공개" : "비공개";
            case "mode", "wait-mode" -> view.modeLabel();
            case "field-code" -> view.codeText();
            case "field-port" -> view.port();
            case "field-address" -> view.address();
            case "recent" -> view.recentEnabled() ? view.recentLabel() : "최근 IP 없음";
            default -> "";
        };
    }

    private static String placeholder(String id) {
        return switch (id) {
            case "field-name" -> "방 이름";
            case "field-code" -> "방 코드";
            case "field-port" -> "포트";
            case "field-address" -> "상대 IP";
            default -> "";
        };
    }

    public static String modeLabel(RelayRoomMode mode) {
        return mode == null ? RelayRoomMode.CLASSIC.label() : mode.label();
    }

    private static int baseline(Graphics2D g, Rectangle box) {
        return box.y + box.height / 2 + g.getFontMetrics().getAscent() / 2 - 1;
    }

    private static String clip(Graphics2D g, String text, int maxWidth) {
        if (text == null || text.isEmpty() || GameTheme.width(g, text) <= maxWidth) return text == null ? "" : text;
        String clipped = text;
        while (clipped.length() > 1 && GameTheme.width(g, clipped + "…") > maxWidth) clipped = clipped.substring(0, clipped.length() - 1);
        return clipped + "…";
    }
}
