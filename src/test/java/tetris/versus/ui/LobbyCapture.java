package tetris.versus.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.setting.ScreenSize;
import tetris.ui.GamePanel;
import tetris.ui.versus.P2pLobbyPanel;

/** 로비 화면 검수용 오프스크린 캡처. 테스트 스위트에는 넣지 않는다 */
public final class LobbyCapture {
    private LobbyCapture() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(System.getProperty("tetris.capture.dir", "build/verification/rooms"));
        Files.createDirectories(out);
        var medium = GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
        capture(out, "list-medium", Pageish.LIST, medium.width, medium.height);
        capture(out, "create-medium", Pageish.CREATE, medium.width, medium.height);
        capture(out, "code-medium", Pageish.CODE, medium.width, medium.height);
        capture(out, "wait-medium", Pageish.WAIT, medium.width, medium.height);
        capture(out, "lan-medium", Pageish.LAN, medium.width, medium.height);
        capture(out, "list-1080", Pageish.LIST, 1920, 1080);
        capture(out, "create-1080", Pageish.CREATE, 1920, 1080);
        capture(out, "code-1080", Pageish.CODE, 1920, 1080);
        capture(out, "wait-1080", Pageish.WAIT, 1920, 1080);
        capture(out, "lan-1080", Pageish.LAN, 1920, 1080);
        System.out.println("wrote " + out.toAbsolutePath());
    }

    private enum Pageish { LIST, CREATE, CODE, WAIT, LAN }

    private static void capture(Path out, String name, Pageish page, int width, int height) throws Exception {
        P2pLobbyPanel panel = new P2pLobbyPanel(new P2pLobbyPanel.Actions() {
            @Override public void host(String port) { }
            @Override public void connect(String address, String port) { }
            @Override public void selectMode(tetris.feature.versus.api.BattleMode mode) { }
            @Override public void selectRaid(tetris.feature.raid.api.RaidMode mode) { }
            @Override public void toggleReady() { }
            @Override public void cancelOrBack() { }
            @Override public void hostInternet() { }
            @Override public void joinInternet(String code) { }
            @Override public void refreshRooms() { }
            @Override public void joinPublic(int routeIndex, String id) { }
        });
        panel.setRooms(List.of(
                new InternetRelay.ListedRoom(0, "메인", "A1", "금요일 한 판", "classic", false, 1),
                new InternetRelay.ListedRoom(0, "메인", "B2", "아이템 연습", "item", true, 1),
                new InternetRelay.ListedRoom(1, "예비", "C3", "시간제한", "timed", false, 1),
                new InternetRelay.ListedRoom(0, "메인", "D4", "협동 레이드", "raid-coop", true, 1)));
        panel.setPreferredSize(new java.awt.Dimension(width, height));
        panel.setSize(width, height);
        panel.prepareCreate("금요일 한 판", false, tetris.relay.RelayRoomMode.ITEM);
        switch (page) {
            case CREATE -> click(panel, "btn-create");
            case CODE -> {
                panel.codeField().setText("K7QM2X");
                click(panel, "btn-code");
            }
            case LAN -> click(panel, "tab-lan");
            case WAIT -> {
                panel.setInternetRoom("K7Q-M2X");
                panel.render(new tetris.feature.network.lobby.LobbySnapshot(
                        tetris.feature.network.lobby.LobbySnapshot.Role.HOST,
                        tetris.feature.network.lobby.LobbySnapshot.State.LOBBY,
                        tetris.feature.versus.api.MatchConfig.of(tetris.feature.versus.api.BattleMode.CLASSIC),
                        1, true, false, "peer"),
                        "상대 접속 대기 중");
            }
            case LIST -> { }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        ImageIO.write(image, "png", out.resolve(name + ".png").toFile());
    }

    private static void click(P2pLobbyPanel panel, String id) {
        java.awt.Rectangle box = panel.hitScreenBounds(id);
        panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, box.x + 8, box.y + 4, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }
}
