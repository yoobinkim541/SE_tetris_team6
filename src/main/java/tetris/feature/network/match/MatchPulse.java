package tetris.feature.network.match;

/** 호스트 엔진의 주기 pulse 공급자 (D-07). tick은 lobby dispatcher(앱은 EDT) 스레드에서 실행되어야 한다 */
@FunctionalInterface
public interface MatchPulse {
    /** periodMillis마다 tick을 dispatcher로 넘긴다. 반환값을 닫으면 더 이상 tick하지 않는다 */
    Handle start(int periodMillis, Runnable tick);

    @FunctionalInterface
    interface Handle extends AutoCloseable {
        @Override
        void close();
    }
}
