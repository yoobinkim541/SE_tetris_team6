package tetris.feature.raid.api;

/** 보스 패턴 (§3.2). 이름과 아이콘 파일 키는 화면이 쓴다 */
public enum PatternId {
    CLOUD("cloud", "점액 구름"),
    SUMMON_BLOCK("summon", "덩어리 소환"),
    ROCK_THROW("rock", "바위 투척"),
    PILLAR("pillar", "기둥 세우기"),
    CURSE_BLOCKS("curse", "저주 블록"),
    SEAL_PREVIEW("seal", "미리보기 봉인"),
    OVERLOAD("overload", "과부하"),
    CHAIN("chain", "연결 공격"),
    REVERSE("reverse", "반전 주문"),
    SPLIT("split", "분열"),
    EARTHQUAKE("quake", "지진"),
    FIRE_BREATH("fire", "화염 브레스");

    private final String assetKey;
    private final String displayName;

    PatternId(String assetKey, String displayName) {
        this.assetKey = assetKey;
        this.displayName = displayName;
    }

    public String assetKey() {
        return assetKey;
    }

    public String displayName() {
        return displayName;
    }

    /** 화면·엔진이 같이 쓰는 효과 설명. 봉인은 바로 다음 1개를 가리지 않는다 */
    public String description() {
        return switch (this) {
            case SEAL_PREVIEW -> "바로 다음 1개는 보이고, 2·3번째와 홀드만 가린다";
            case CURSE_BLOCKS -> "보여 준 다음 블록은 그대로 두고 그 뒤 2개를 S/Z로 바꾼다";
            default -> displayName;
        };
    }
}
