# 에셋 출처

보스 레이드 그림은 전부 Codex CLI 내장 이미지 생성(`gpt-image-2`)으로 2026-11-20에 만들었다. 밈 사진, 다른 게임 그림, 그 밖의 제3자 이미지는 쓰지 않았다. 생성 원본은 저장소 밖 `assets-work/raw/`에만 두고, 아래 파일은 그 원본을 맞춘 결과다.

후처리: Pillow로 규격 크기에 맞추고, 배경은 JPEG, 나머지는 PNG(optimize)로 저장했다. 보스·아이콘·로고·카드는 가장자리를 투명하게 남겨 모서리 알파가 0이다. 2026-11-21에 그림을 다시 만들지 않고 용량만 줄였다. 배경은 같은 원본을 1920×1080으로 다시 샘플해 JPEG progressive 품질 86, 4:4:4로 저장했다. 화면보다 큰 PNG는 2560×1440에서 실제로 그려지는 크기에 맞췄다. 프레임·탭만 256색 팔레트다.

| 파일 | 도구 | 날짜 | 프롬프트 요약 | 후처리 |
|---|---|---|---|---|
| `bg/raid.jpg` | gpt-image-2 (기존 `style-a-bg.png` 재사용) | 2026-11-20 | 밤하늘 판타지 아케이드 배경. 남색·보라 성운, 가장자리 수정 섬, 가운데는 어둡게 | 1920×1080, 원본 Lanczos, JPEG progressive 86 4:4:4 |
| `bg/menu.jpg` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 같은 세계의 더 넓은 풍경. 가운데 위에 로고용 은은한 빛 | 1920×1080, 원본 Lanczos, JPEG progressive 86 4:4:4 |
| `bg/versus.jpg` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 같은 세계. 좌우에 보드용 어두운 세로 구역, 가운데 먼 수정 첨탑 | 1920×1080, 원본 Lanczos, JPEG progressive 86 4:4:4 |
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
| `icon/pattern_cloud.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 굵은 점액 구름 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_summon.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 떨어지는 테트로미노 덩어리 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_rock.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 날아가는 바위 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_pillar.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 룬이 있는 기둥 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_curse.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 해골 표시 테트로미노 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_seal.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 봉인된 눈 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_overload.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 번개와 아래 화살 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_chain.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 사슬로 이어진 두 블록 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_reverse.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 좌우가 바뀌는 화살 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_split.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 둘로 갈라지는 방울 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_quake.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 금 간 땅과 충격파 아이콘 | 72×72 맞춤, PNG |
| `icon/pattern_fire.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 화염 브레스 아이콘 | 72×72 맞춤, PNG |
| `card/sharp.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 칼날 블록. 글자 없음 | 300×450 맞춤, PNG |
| `card/shield.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 방패. 글자 없음 | 300×450 맞춤, PNG |
| `card/timewarp.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 모래시계. 글자 없음 | 300×450 맞춤, PNG |
| `card/cheer.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 빛나는 하트. 글자 없음 | 300×450 맞춤, PNG |
| `card/telescope.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 망원경. 글자 없음 | 300×450 맞춤, PNG |
| `card/luck.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 클로버와 황금 블록. 글자 없음 | 300×450 맞춤, PNG |
| `card/sweep.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 장식 카드 프레임, 수정 빗자루. 글자 없음 | 300×450 맞춤, PNG |
| `card/back.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 같은 카드의 뒷면, 대칭 수정 문양. 글자 없음 | 300×450 맞춤, PNG |
| `ui/logo.png` | gpt-image-2, Codex CLI 내장 | 2026-11-20 | 빛나는 수정 블록으로 만든 SE TETRIS 로고, 투명 배경 | 960×360 맞춤, PNG |
| `ui/board-crown.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 보드 위에 앉는 수정 산맥과 작은 성. 글자 없음 | 480×128, #00FF00 크로마키·디스필, PNG |
| `ui/gem-tile.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 무채색 다면 보석 타일. 팔레트 곱색용, 글자 없음 | 40×40, 채도 0, PNG |
| `ui/frame-cyan.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 청록 수정 네온 프레임. 모서리 장식, 곧은 레일, 글자 없음 | 256×256, 9슬라이스 64px, 가운데 투명, 팔레트 256 |
| `ui/frame-magenta.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 자홍 수정 네온 프레임. 글자 없음 | 256×256, 9슬라이스 64px, 가운데 투명, 팔레트 256 |
| `ui/frame-violet.png` | gpt-image-2 후처리 (청록 프레임 색상 이동) | 2026-11-21 | 보라 수정 네온 프레임. 청록 프레임과 같은 모서리 | 256×256, 9슬라이스 64px, 가운데 투명, 팔레트 256 |
| `ui/tab-cyan.png` | gpt-image-2 후처리 (청록 프레임에 유리 채움) | 2026-11-21 | 청록 사이드 탭. 어두운 유리 가운데, 글자 없음 | 192×192, 9슬라이스 48px, 팔레트 256 |
| `ui/tab-magenta.png` | gpt-image-2 후처리 (자홍 프레임에 유리 채움) | 2026-11-21 | 자홍 사이드 탭. 어두운 유리 가운데, 글자 없음 | 192×192, 9슬라이스 48px, 팔레트 256 |
| `ui/button.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 청록 수정 알약 버튼. 글자 없음 | 512×128, 3슬라이스 64px |
| `ui/button-selected.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 더 밝은 선택 상태 알약 버튼. 글자 없음 | 512×128, 3슬라이스 64px |
| `ui/capsule.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | HUD 캡슐. 수정 끝, 어두운 유리, 글자 없음 | 256×96, 3슬라이스 48px |
| `ui/badge-p1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 청록 다이아몬드 배지. 글자 없음 | 76×76, PNG |
| `ui/badge-p2.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 자홍 별 배지. 글자 없음 | 76×76, PNG |
| `ui/banner-warning.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 분홍 경고 리본. 글자·기호 없음 | 900×160, PNG |
| `ui/island-1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 떠 있는 수정 섬, 청록 폭포 | 225×225, PNG |
| `ui/island-2.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 다른 실루엣의 수정 섬, 자홍 군집 | 225×225, PNG |
| `ui/moon.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 창백한 보름달. 하늘 없음 | 225×225, PNG |
| `raid/ledge-jelly.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 같은 슬라임 왕이 수정 턱을 짚고 내려다봄. 글자 없음 | 1680×720, #00FF00 크로마키·디스필, pngquant 256 |
| `raid/ledge-golem.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 같은 골렘이 수정 턱을 짚고 내려다봄. 글자 없음 | 1680×720, #00FF00 크로마키·디스필, pngquant 256 |
| `raid/ledge-witch.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 같은 마녀가 양손으로 수정 턱을 짚고 내려다봄. 글자 없음 | 1680×720, #00FF00 크로마키·디스필, pngquant 256 |
| `raid/ledge-dragon.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 같은 기계 용이 발톱으로 수정 턱을 짚고 내려다봄. 글자 없음 | 1680×720, #00FF00 크로마키·디스필, pngquant 256 |
| `raid/ledge-golden.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 같은 황금 테트로미노 정령이 수정 턱을 짚고 내려다봄. 글자 없음 | 1680×720, #00FF00 크로마키·디스필, pngquant 256 |
| `raid/scene-raid.jpg` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 세로 레이드 배경. 달, 수정 산, 가장자리 섬, 아래는 어둡게. 보스·보드·UI 없음 | 1200×1440, JPEG 86 progressive |
| `raid/scene-raid-wide.jpg` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 전체 화면용 가로 배경. 가운데는 어둡고, 양옆에 섬·수정·달. 보스·보드·UI 없음 | 1920×1080, JPEG 86 progressive |
| `raid/hp-frame.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 수정 HP 바 프레임. 왼쪽 보석 캡, 오른쪽 퍼센트 칸. 글자 없음 | 1080×161, 속 투명, pngquant 256 |
| `raid/nametab-p1.png` | gpt-image-2, Codex CLI 내장 | 2026-11-21 | 청록 이름 탭. 왼쪽 다이아몬드. 글자 없음 | 520×136, pngquant 256 |
| `raid/nametab-p2.png` | gpt-image-2 후처리 (빨간 분홍을 보라 #B44CFF–#E040FB 쪽으로) | 2026-11-21 | 보라 이름 탭. 오른쪽 별. 글자 없음 | 520×136, pngquant 256 |
| `raid/frame-ornate-cyan.png` | gpt-image-2 후처리 (모서리만 남기고 띠를 얇게) | 2026-11-21 | 얇은 청록 수정 보드 프레임. 모서리 군집만, 가운데 투명. 글자 없음 | 512×512, 9슬라이스 64px, 띠 22px, pngquant 256 |
| `raid/frame-ornate-magenta.png` | gpt-image-2 후처리 (청록 프레임의 시안만 +102도, 보라 계열) | 2026-11-21 | 보라 보드 프레임. 청록 프레임과 같은 모서리. 빨간 분홍 아님 | 512×512, 9슬라이스 64px, 띠 22px, pngquant 256 |

## Sounds

효과음과 배경음은 외부 샘플 없이 팀 스크립트 `tools/sfx/make_sfx.py`로 2026-11-20에 합성했다. 제3자 음원·녹음은 쓰지 않았다. 효과음은 16비트 모노 22050 Hz PCM이고, 클릭이 나지 않게 양끝을 짧게 페이드한 뒤 끝 무음만 잘랐다. 배경음은 같은 합성을 16 kHz로 낮춘 16비트 모노 PCM이다. µ-law는 쓰지 않았다. 루프는 끝과 처음이 이어지게 두어 가장자리를 0으로 내리지 않는다. 길이 제한에 맞추려 레이드는 8마디(Am–F–C–G), 메뉴는 6마디(C–Am–G)로 줄였다. 다시 만들려면 저장소 루트에서 `python tools/sfx/make_sfx.py`를 실행한다. 출력은 `src/main/resources/sfx/`이다.

| 파일 | 길이 | 내용 |
|---|---|---|
| `sfx/move.wav` | 0.046초 | 블록 이동 틱 |
| `sfx/rotate.wav` | 0.085초 | 회전. 짧게 올라가는 꺾임 |
| `sfx/hold.wav` | 0.160초 | 홀드. 부드러운 종 |
| `sfx/harddrop.wav` | 0.250초 | 하드드롭. 내려꽂는 소리와 착지 |
| `sfx/lock.wav` | 0.070초 | 착지 클릭 |
| `sfx/clear1.wav` | 0.280초 | 1줄 삭제. 단일 종 |
| `sfx/clear2.wav` | 0.390초 | 2줄 삭제. 두 음 |
| `sfx/clear3.wav` | 0.480초 | 3줄 삭제. 세 음 |
| `sfx/tetris.wav` | 0.750초 | 4줄 삭제. 장조 팡파르 |
| `sfx/tspin.wav` | 0.400초 | T스핀. 올라가는 반짝임 |
| `sfx/combo.wav` | 0.430초 | 콤보. 음이 올라감 |
| `sfx/attack_send.wav` | 0.200초 | 공격을 보냄 |
| `sfx/attack_receive.wav` | 0.260초 | 공격을 맞음 |
| `sfx/boss_spawn.wav` | 1.120초 | 보스 등장 |
| `sfx/boss_attack_warn.wav` | 0.465초 | 보스 패턴 경고 |
| `sfx/boss_hit.wav` | 0.180초 | 보스 타격 |
| `sfx/boss_defeat.wav` | 1.094초 | 보스 처치 |
| `sfx/critical.wav` | 0.265초 | 크리티컬 스파클 |
| `sfx/revive.wav` | 0.780초 | 부활. 올라가는 장조 |
| `sfx/ultimate.wav` | 0.950초 | 필살기 |
| `sfx/card_flip.wav` | 0.090초 | 카드 뒤집기 |
| `sfx/card_pick.wav` | 0.340초 | 카드 선택 |
| `sfx/countdown.wav` | 0.150초 | 카운트 비프 |
| `sfx/go.wav` | 0.360초 | 시작 화음 |
| `sfx/victory.wav` | 1.020초 | 승리 팡파르 |
| `sfx/defeat.wav` | 1.060초 | 패배. 내려가는 단조 |
| `sfx/menu_move.wav` | 0.034초 | 메뉴 이동 틱 |
| `sfx/menu_select.wav` | 0.195초 | 메뉴 결정 |
| `sfx/bgm_raid.wav` | 18.286초 | 레이드 배경음. 16 kHz 신스웨이브 아르페지오 루프 |
| `sfx/bgm_menu.wav` | 20.000초 | 메뉴 배경음. 16 kHz 잔잔한 루프 |

## Fonts

메뉴·제목·라벨은 Do Hyeon, 점수·시간 숫자는 Orbitron이다. 둘 다 SIL Open Font License 1.1이라 앱에 넣어 배포한다. 라이선스 전문은 `src/main/resources/fonts/DoHyeon-OFL.txt`, `src/main/resources/fonts/Orbitron-OFL.txt`이다. Orbitron은 가변 글꼴이라 Java가 기본 굵기만 열 수 있어, 배포 파일은 굵기 500으로 뽑은 정적 글꼴이다.

| 파일 | 저작권 | 라이선스 | 출처 |
|---|---|---|---|
| `fonts/DoHyeon-Regular.ttf` | Copyright 2018 The Do Hyeon Project Authors | SIL OFL 1.1 | https://fonts.google.com/specimen/Do+Hyeon |
| `fonts/Orbitron.ttf` | Copyright 2018 The Orbitron Project Authors, Reserved Font Name "Orbitron" | SIL OFL 1.1 | https://github.com/theleagueof/orbitron |
