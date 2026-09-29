package tetris.feature.rule;

import tetris.component.block.Block;

/** 다음 블록 선택 정책 (BLK-2, BLK-3). 1차: 균등 독립 랜덤. 향후 7-bag 등으로 교체할 수 있다. */
public interface BlockGenerator {
    /** 새 블록 1개를 0° 상태로 생성해 반환 */
    Block next();
}
