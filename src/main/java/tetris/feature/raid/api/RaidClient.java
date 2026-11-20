package tetris.feature.raid.api;

import java.util.function.Consumer;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

/**
 * 레이드 화면이 쓰는 포트. 로컬 레이드(솔로·협동)가 구현한다. 콜백은 EDT에서 오고, 구독 즉시 현재 스냅샷을 한 번 받는다.
 * 솔로는 {@link PlayerId#ONE}만 쓴다.
 */
public interface RaidClient extends AutoCloseable {
    /** 키 입력. 대전과 같은 규칙(반복 이동, press→release 한 번) */
    void send(PlayerCommand command);

    /** 필살기 키. 협동은 두 사람이 1.5초 안에 눌러야 발동, 솔로는 게이지가 차 있으면 바로 발동 */
    void ultimate(PlayerId player);

    /** 보상 카드 선택. CARD_PICK 단계의 선택권자만 유효, index는 제시된 카드 0..2 */
    void pickCard(PlayerId player, int index);

    BattleClient.Subscription subscribe(Consumer<RaidSnapshot> listener);

    @Override
    void close();
}
