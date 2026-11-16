package tetris.feature.save;

import java.util.List;

/**
 * P2P 클라이언트 접속에 성공한 최근 상대 IPv4 주소 저장소 (S10-C2, R3-RECENT).
 */
public interface RecentPeers {
    int MAX = 5;

    List<String> load();

    void record(String address);

    RecentPeers NONE = new RecentPeers() {
        @Override
        public List<String> load() {
            return List.of();
        }

        @Override
        public void record(String address) {
        }
    };
}
