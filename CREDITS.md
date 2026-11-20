# 에셋 출처

보스 레이드 그림은 전부 Codex CLI 내장 이미지 생성(`gpt-image-2`)으로 2026-11-20에 만들었다. 밈 사진, 다른 게임 그림, 그 밖의 제3자 이미지는 쓰지 않았다. 생성 원본은 저장소 밖 `assets-work/raw/`에만 두고, 아래 파일은 그 원본을 맞춘 결과다.

후처리: Pillow로 규격 크기에 맞추고, 배경은 JPEG 품질 85, 나머지는 PNG(optimize)로 저장했다. 보스·아이콘·로고·카드는 가장자리를 투명하게 남겨 모서리 알파가 0이다.

| 파일 | 도구 | 날짜 | 프롬프트 요약 | 후처리 |
|---|---|---|---|---|
| `bg/raid.jpg` | gpt-image-2 (기존 `style-a-bg.png` 재사용) | 2026-11-20 | 밤하늘 판타지 아케이드 배경. 남색·보라 성운, 가장자리 수정 섬, 가운데는 어둡게 | 1920×1080 커버 크롭, JPEG 85 |
| `bg/menu.jpg` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 같은 세계의 더 넓은 풍경. 가운데 위에 로고용 은은한 빛 | 1920×1080 커버 크롭, JPEG 85 |
| `bg/versus.jpg` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 같은 세계. 좌우에 보드용 어두운 세로 구역, 가운데 먼 수정 첨탑 | 1920×1080 커버 크롭, JPEG 85 |
| `boss/jelly_lv1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 왕관을 쓴 거대 슬라임 왕, 청록 젤리, 투명 배경 | 768×768 맞춤, PNG |
| `boss/jelly_lv2.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | Lv1과 같은 슬라임 왕에 수정 견갑·큰 왕관·은은한 오라 | 768×768 맞춤, PNG |
| `boss/jelly_lv3.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 슬라임 왕의 최종 형태. 강한 오라, 가시 수정 갑옷 | 768×768 맞춤, PNG |
| `boss/golem_lv1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 룬이 빛나는 고대 돌 거인, 투명 배경 | 768×768 맞춤, PNG |
| `boss/golem_lv2.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 골렘에 룬 갑판·수정 견갑·오라 | 768×768 맞춤, PNG |
| `boss/golem_lv3.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 골렘의 최종 형태. 강한 오라, 용암 수정 균열 | 768×768 맞춤, PNG |
| `boss/witch_lv1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 지팡이와 박쥐를 둔 밤의 마녀, 투명 배경 | 768×768 맞춤, PNG |
| `boss/witch_lv2.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 마녀에 보석 모자·큰 지팡이 보석·오라 | 768×768 맞춤, PNG |
| `boss/witch_lv3.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 마녀의 최종 형태. 망토가 날개처럼, 강한 오라 | 768×768 맞춤, PNG |
| `boss/dragon_lv1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 네온 회로가 있는 기계 용, 투명 배경 | 768×768 맞춤, PNG |
| `boss/dragon_lv2.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 기계 용에 장갑·밝은 회로·오라 | 768×768 맞춤, PNG |
| `boss/dragon_lv3.png` | gpt-image-2 편집 (같은 세션) | 2026-11-20 | 같은 기계 용의 최종 형태. 날개 펼침, 강한 오라 | 768×768 맞춤, PNG |
| `boss/golden_lv1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 작은 황금 테트로미노 정령, 투명 배경 | 768×768 맞춤, PNG |
| `icon/pattern_cloud.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 굵은 점액 구름 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_summon.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 떨어지는 테트로미노 덩어리 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_rock.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 날아가는 바위 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_pillar.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 룬이 있는 기둥 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_curse.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 해골 표시 테트로미노 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_seal.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 봉인된 눈 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_overload.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 번개와 아래 화살 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_chain.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 사슬로 이어진 두 블록 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_reverse.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 좌우가 바뀌는 화살 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_split.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 둘로 갈라지는 방울 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_quake.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 금 간 땅과 충격파 아이콘 | 128×128 맞춤, PNG |
| `icon/pattern_fire.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 화염 브레스 아이콘 | 128×128 맞춤, PNG |
| `card/sharp.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 칼날 블록. 글자 없음 | 384×576 맞춤, PNG |
| `card/shield.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 방패. 글자 없음 | 384×576 맞춤, PNG |
| `card/timewarp.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 모래시계. 글자 없음 | 384×576 맞춤, PNG |
| `card/cheer.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 빛나는 하트. 글자 없음 | 384×576 맞춤, PNG |
| `card/telescope.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 망원경. 글자 없음 | 384×576 맞춤, PNG |
| `card/luck.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 클로버와 황금 블록. 글자 없음 | 384×576 맞춤, PNG |
| `card/sweep.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 빗자루. 글자 없음 | 384×576 맞춤, PNG |
| `card/back.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 같은 카드의 뒷면, 대칭 수정 문양. 글자 없음 | 384×576 맞춤, PNG |
| `ui/logo.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 빛나는 수정 블록으로 만든 SE TETRIS 로고, 투명 배경 | 1200×450 맞춤, PNG |

## Sounds

아직 없음.
