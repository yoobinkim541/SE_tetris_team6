package tetris.ui;

/** 메뉴 항목 = 라벨 + 동작. 기존 component/button/* 클래스를 대체하며, 항목을 목록에 추가만 하면 확장된다. */
public record MenuItem(String label, Runnable action) {
}
