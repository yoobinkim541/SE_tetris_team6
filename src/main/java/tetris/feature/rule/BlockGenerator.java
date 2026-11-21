package tetris.feature.rule;

import tetris.component.block.Block;

/** 다음 블록 선택 정책 (BLK-2, BLK-3). 실제 게임은 BagBlockGenerator(가중치 7-bag)를 쓴다. */
public interface BlockGenerator {
    /** 새 블록 1개를 0° 상태로 생성해 반환 */
    Block next();
}
