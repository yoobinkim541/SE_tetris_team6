package tetris.feature.item;

/** 화면에 보여줄 지속 효과 한 건 (정보 패널의 x2 7, SLOW 3). remainingBlocks는 0..10 */
public record ActiveEffect(ItemType type, int remainingBlocks) {
}
