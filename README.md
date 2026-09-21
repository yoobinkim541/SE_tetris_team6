# TETRIS - Team6
김유빈 심재윤 심규민

## 목차
1. [Project Overview](#1-project-overview)
2. [Git Convention](#2-git-convention)


---

## 1. Project Overview
| 항목 | 내용 |
|---|---|
| 프로그램 | SE Tetris Term project  |
| 팀 | Team 6 |
| 프로젝트명 | Tetris |
| 프로젝트 기간 | `2026.09.3 ~ 2026.12.??` |
| 프로젝트 상태 | `개발중` |
| Repository | `SE_tetris_team6` |
| Notion | [Software Enginering Term Project Tetris](https://app.notion.com/p/Software-Enginering-Term-Project-Tetris-3e1a13e7df00802e96f8fe312fa590ea) |

---

## 2. Git Convention

### 브랜치 전략 (Branching Strategy)
- main : 제품 출시 및 배포용 브랜치 (직접 commit 금지)
- develop : 통합 개발 브랜치 (다음 버전을 개발)
- develop-[NAME] : 개인별 통합 브랜치
- feature/기능명 : 새로운 기능을 개발하는 브랜치 (예시 : feature/login)
- fix/버그명 : 버그를 수정하는 브랜치 (예시 : fix/error-404)

### Commit Message
```text
<type>: <summary>
```

### 커밋 메시지 규칙(Commit Message Convention)
커밋 메시지는 타입 : 메시지 내용 형태로 작성해주세요

| 타입 | 의미 |
|---|---|
| Feat | 새로운 기능 추가 |
| Fix | 버그 수정 |
| Docs | 문서 수정(README 등) |
| Style | 코드 포맷팅, 세미콜론 누락 등(코드 변경 없는 경우) |
| Refactor | 코드 리팩토링 |

#### Example
```text
feat: 산업 뉴스 수집 기능 추가
fix: 중복 문서 제거 오류 수정
docs: 프로젝트 실행 방법 추가
data: 반도체 산업 키워드 목록 업데이트
```

### Pull Request Rules
1. feature 브랜치 작업 완료 후 `develop`(통합 개발 환경) 또는 `develop-[name]`(개인별) 브랜치로 PR을 보냅니다.
2. 충돌(Conflict) 발생 시 작업자가 직접 해결 후 push 합니다.

#### 참고사항
- 하나의 PR에는 하나의 주요 목적만 포함합니다.
- PR 본문에 작업 내용과 테스트 결과를 작성합니다.
- 직접 `main` Branch에 Push하지 않습니다.
- 최소 1명 이상의 팀원 확인 후 Merge합니다.
- Merge가 필요한 경우 팀 채널에 PR 링크와 내용을 공유합니다.

### Pull Request Template
```markdown
## 작업 내용
-

## 변경 이유
-

## 테스트 결과
- [ ] 로컬 실행 확인
- [ ] 기존 기능 정상 동작 확인
- [ ] 오류 및 예외 상황 확인

## 참고 사항
-

## 관련 Issue
- Closes #
```

---
