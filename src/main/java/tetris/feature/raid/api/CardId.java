package tetris.feature.raid.api;

/** 보스 처치 보상 카드 (§5) */
public enum CardId {
    SHARP_BLOCKS("sharp", "날카로운 블록", "이번 판 데미지 +10%"),
    SHIELD("shield", "방패", "다음 패턴 1회 무효"),
    TIME_WARP("timewarp", "시간 왜곡", "다음 보스 첫 패턴 +4조각"),
    CHEER("cheer", "응원", "게이지 +50"),
    TELESCOPE("telescope", "망원경", "NEXT 미리보기 +2"),
    LUCK("luck", "행운", "럭키 블록 확률 2배 (보스 1마리)"),
    SWEEP("sweep", "대청소", "내 보드 맨 아래 2줄 제거");

    private final String assetKey;
    private final String displayName;
    private final String description;

    CardId(String assetKey, String displayName, String description) {
        this.assetKey = assetKey;
        this.displayName = displayName;
        this.description = description;
    }

    public String assetKey() {
        return assetKey;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
