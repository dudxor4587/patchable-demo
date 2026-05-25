# Patchable Demo

[Patchable](https://github.com/dudxor4587/patchable) 라이브러리의 데모 프로젝트.

## 블로그 시리즈

| # | 제목 | 내용 |
|---|------|------|
| 1편 | [Java는 왜 PATCH를 배려하지 않는가](docs/01-Java는%20왜%20PATCH를%20배려하지%20않는가.md) | 기존 해법 5개 비교 + 한계 분석 |
| 2편 | [Java에서 PATCH를 어떻게 풀어야할까](docs/02-Java에서%20PATCH를%20어떻게%20풀어야할까.md) | 라이브러리 디자인 트레이드오프 |
| 3편 | [Java에서 PATCH를 이렇게 풀었다](docs/03-Java에서%20PATCH를%20이렇게%20풀었다.md) | 구현 + 배포 + 실제 적용 |

## 프로젝트 구조

```
src/main/java/com/patchabledemo/
├── comparison/    ← 1편: 기존 해법 비교 코드 (Jackson, JsonNullable, JSON Patch, BeanUtils, MapStruct)
├── member/        ← 기본 Member 엔티티 + PUT/PATCH
└── sample/        ← 3편: Patchable 라이브러리 적용 샘플
```

## 실행

```bash
./gradlew bootRun
```

```bash
# PATCH 테스트
curl -X PATCH http://localhost:8080/api/sample/members/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "민수", "bio": null}'
```

## 요구사항

- Java 17+
- Spring Boot 3.x+
