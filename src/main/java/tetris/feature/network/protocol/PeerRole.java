package tetris.feature.network.protocol;

/** 연결 안에서의 역할. 호스트가 두 GameSession·시계·승패를 소유한다 (호스트 권위) */
public enum PeerRole {
    HOST,
    CLIENT
}
