package tetris.ui.fx;

/**
 * 클래스패스 {@code /sfx/<이름>.wav}. 파일은 {@code tools/sfx/make_sfx.py}가 만든다.
 */
public enum SoundId {
    MOVE("move"),
    ROTATE("rotate"),
    HOLD("hold"),
    HARDDROP("harddrop"),
    LOCK("lock"),
    CLEAR1("clear1"),
    CLEAR2("clear2"),
    CLEAR3("clear3"),
    TETRIS("tetris"),
    TSPIN("tspin"),
    COMBO("combo"),
    ATTACK_SEND("attack_send"),
    ATTACK_RECEIVE("attack_receive"),
    BOSS_SPAWN("boss_spawn"),
    BOSS_ATTACK_WARN("boss_attack_warn"),
    BOSS_HIT("boss_hit"),
    BOSS_DEFEAT("boss_defeat"),
    CRITICAL("critical"),
    REVIVE("revive"),
    ULTIMATE("ultimate"),
    CARD_FLIP("card_flip"),
    CARD_PICK("card_pick"),
    COUNTDOWN("countdown"),
    GO("go"),
    VICTORY("victory"),
    DEFEAT("defeat"),
    MENU_MOVE("menu_move"),
    MENU_SELECT("menu_select"),
    BGM_RAID("bgm_raid"),
    BGM_MENU("bgm_menu");

    private final String stem;

    SoundId(String stem) {
        this.stem = stem;
    }

    public String stem() {
        return stem;
    }

    public String resourcePath() {
        return "/sfx/" + stem + ".wav";
    }

    /** 배경음처럼 이어서 트는 소리 */
    public boolean loops() {
        return this == BGM_RAID || this == BGM_MENU;
    }
}
