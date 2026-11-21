package tetris.feature.save;

/**
 * {@code sun.awt.exception.handler}. EDT 콜백 예외를 파일에 남기고, 이벤트 펌프는 계속 돈다.
 * 핸들러가 예외를 받으면 Swing은 그 틱만 버리고 다음 이벤트를 처리한다.
 */
public final class EdtExceptionHandler {
    public void handle(Throwable error) {
        ErrorLog.shared().report("EDT", error);
        error.printStackTrace(System.err);
    }
}
