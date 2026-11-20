package tetris.feature.raid.api;

/** 보스 4종 + 숨은 보스 (§3.1). 에셋 파일 이름은 {@link #assetKey()} */
public enum BossId {
    JELLY_KING("jelly", "젤리 킹"),
    STONE_GOLEM("golem", "석상 골렘"),
    NIGHT_WITCH("witch", "밤의 마녀"),
    MECHA_DRAGON("dragon", "기계 용"),
    /** 숨은 보스. 패턴 없이 15초 뒤 도망간다 */
    GOLDEN_TETRO("golden", "황금 테트로");

    private final String assetKey;
    private final String displayName;

    BossId(String assetKey, String displayName) {
        this.assetKey = assetKey;
        this.displayName = displayName;
    }

    public String assetKey() {
        return assetKey;
    }

    public String displayName() {
        return displayName;
    }
}
