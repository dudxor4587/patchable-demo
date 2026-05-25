## 배경

[1편](01-Java는%20왜%20PATCH를%20배려하지%20않는가.md)에서 기존 해법들이 각자 무엇을 양보하는지 살펴봤고, [2편](02-Java에서%20PATCH를%20어떻게%20풀어야할까.md)에서는 직접 라이브러리를 디자인하면서 마주친 트레이드오프들을 정리했다.

이제 실제로 만들어볼 차례다. 그 전에, 이 라이브러리를 왜 만들려고 했는지 실제 코드로 한 번 더 짚고 가자.

---

## 실무에서 마주친 코드

실제 프로젝트에서 댓글 수정 API 를 이렇게 짰다:

```java
public record CommentRequest(
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        String content,
        String imageUrl,
        boolean deleteImage,
        UUID parentCommentId
) {}
```

```java
public void update(String content, String imageUrl, boolean deleteImage) {
    this.content = content;

    if (deleteImage) {
        this.imageUrl = null;
        return;
    }

    if (imageUrl != null) {
        this.imageUrl = imageUrl;
    }
    // else: 기존 imageUrl 보존
}
```

PUT 이라 전체를 다 받는 구조인데, 안을 보면 **PATCH 시맨틱을 수동으로 구현하고 있다.** `boolean deleteImage` 는 "명시적 삭제" 를 표현하는 수동 플래그이고, `if (imageUrl != null)` 은 "null 이면 skip" 의 수동 분기다. PUT 으로 도망갔는데 결국 안에서 PATCH 를 손으로 짜고 있는 셈.

Patchable 로 바꾸면:

```java
@PatchOf(value = Comment.class, method = "update")
public record CommentPatchRequest(
    @NotBlank String content,
    PatchField<String> imageUrl    // Unset = skip, Value = update, Delete = delete
) {}
```

`boolean deleteImage` 가 사라지고, 도메인 메서드의 수동 분기도 사라진다. 이게 이 라이브러리의 존재 이유다.

---

## 처음 그린 그림

2편에서 가장 끌렸던 모양은 이거였다:

```java
@Patchable
public class Member {
    public void updateMember(String name, String email, String bio) { ... }

    // 라이브러리가 자동 생성:
    // public void partialUpdate(String name, String email, String bio) {
    //     updateMember(
    //         name != null ? name : this.name,
    //         ...
    //     );
    // }
}

// 사용:
member.partialUpdate(request.name(), request.email(), request.bio());
```

PUT 처럼 자연스럽게 `member.뭐()` 형태로 호출. 도메인 메서드는 그대로 보존. 보일러플레이트 zero.

---

## Java 는 extension method 가 없다

구현에 들어가자마자 벽에 부딪혔다.

`member.partialUpdate(...)` 가 동작하려면 `partialUpdate` 메서드가 **Member 클래스 안에 존재해야** 한다. 라이브러리가 그 메서드를 자동 생성하려면 **기존 클래스에 메서드를 추가**할 수 있어야 한다.

표준 어노테이션 프로세서는 **새 파일 생성만 가능하고, 기존 클래스 수정은 불가능**하다.

Lombok 이 이걸 하긴 한다. `@Getter` 를 붙이면 컴파일 타임에 getter 메서드가 끼워넣어지니까. 근데 Lombok 은 `com.sun.tools.javac` 내부 API — 비표준 트릭 — 을 사용한다. JDK 버전마다 깨질 수 있고, IDE 플러그인 의존이 생긴다.

**비표준 트릭을 받아들이지 않는 한, `member.뭐()` 형태는 Java 에서 불가능하다.**

Kotlin 이라면:
```kotlin
fun Member.applyPatch(req: MemberPatchRequest) {
    updateMember(req.name ?: this.name, ...)
}

member.applyPatch(request)   // Member 클래스 안 건드림, 호출은 멤버처럼
```

extension function 이 있어서 양보 없이 풀린다. C# 도 마찬가지. **Java 만 이게 없다.**

1편에서 "Java 는 왜 PATCH 를 배려하지 않는가" 라고 물었는데, 그 답의 한 조각이 여기 있었다. 언어 차원에서 extension method 가 지원되지 않는 것.

---

## 그래서 어떻게 됐나

`member.뭐()` 를 포기하고, **별도 클래스가 도메인 메서드를 호출하는 형태**로 갔다.

```java
@Component
public class MemberProfilePatchPatcher {

    public void apply(Member target, MemberProfilePatch source) {
        target.updateMember(
                source.name() != null ? source.name() : target.getName(),
                source.email() != null ? source.email() : target.getEmail(),
                source.nickname() != null ? source.nickname() : target.getNickname(),
                resolveString(source.phoneNumber(), target.getPhoneNumber()),
                resolveString(source.address(), target.getAddress()),
                resolveString(source.bio(), target.getBio())
        );
    }

    private static String resolveString(PatchField<String> field, String current) {
        return switch (field) {
            case PatchField.Unset<String>() -> current;
            case PatchField.Value<String>(var v) -> v;
            case PatchField.Delete<String>() -> null;
        };
    }
}
```

Spring 빈으로 등록하고, DI 로 주입해서 사용한다:

```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository repository;
    private final MemberProfilePatchPatcher patcher;

    @Transactional
    public Member patch(Long id, MemberProfilePatch request) {
        Member member = repository.findById(id).orElseThrow();
        patcher.apply(member, request);
        return member;
    }
}
```

모양만 보면 MapStruct 와 비슷하다. `patcher.apply(member, request)` — 한 줄. 외부에서 보면 MapStruct 의 `mapper.patch(request, member)` 와 다를 게 없어 보인다.

---

## 근데 안에서 일어나는 일이 다르다

**MapStruct 가 생성하는 코드:**

```java
public void patch(MemberPatchRequest request, Member member) {
    if (request.name() != null) member.setName(request.name());
    if (request.email() != null) member.setEmail(request.email());
    if (request.bio() != null) member.setBio(request.bio());
}
```

개별 setter 를 호출한다. 도메인 메서드 (`updateMember`) 는 완전히 우회된다.

**우리 patcher 가 생성하는 코드:**

```java
public void apply(Member target, MemberProfilePatch source) {
    target.updateMember(
        source.name() != null ? source.name() : target.getName(),
        // ...
    );
}
```

**도메인 메서드 (`updateMember`) 를 그대로 호출**한다. setter 는 쓰지 않는다.

이 차이가 왜 중요한가? 도메인 메서드 안에 있는 **불변식 검증이 그대로 동작**하기 때문이다:

```java
public void updateMember(String name, String email, String nickname,
                         String phoneNumber, String address, String bio) {
    if (name == null || name.isBlank())
        throw new IllegalArgumentException("name 은 필수입니다");
    if (!email.contains("@"))
        throw new IllegalArgumentException("유효하지 않은 이메일");
    this.name = name;
    this.email = email;
    // ...
}
```

MapStruct 는 setter 로 우회하니까 이 검증을 건너뛴다. 빈 문자열이든 @ 없는 이메일이든 그대로 저장된다. **우리 patcher 는 도메인 메서드를 호출하니까 검증이 돌아간다.** 잘못된 입력이 들어오면 도메인이 거부한다.

---

## PatchField — 3 상태 표현

2편에서 설계한 `PatchField<T>` sealed 타입도 구현했다:

```java
public sealed interface PatchField<T> {
    record Unset<T>()        implements PatchField<T> {}  // 미지정 → skip
    record Value<T>(T value) implements PatchField<T> {}  // 값 설정 → update
    record Delete<T>()       implements PatchField<T> {}  // 비우기 → delete
}
```

DTO 에서 이렇게 사용한다:

```java
@PatchOf(value = Member.class, method = "updateMember")
public record MemberProfilePatch(
    String name,                          // plain — null 이면 기존 값 유지
    String email,
    String nickname,
    PatchField<String> phoneNumber,       // 3 상태 — Unset(skip) / Value(update) / Delete(delete)
    PatchField<String> address,
    PatchField<String> bio
) {}
```

필수 필드 (`name`, `email`, `nickname`) 는 평범한 String — `null` 이면 skip.
옵셔널 필드 (`phoneNumber`, `address`, `bio`) 는 `PatchField<String>` — 3 상태 표현 가능.

2편에서 정리한 규칙 그대로다: **도메인의 nullability 가 wrapping 을 결정한다.**

Jackson deserializer 가 JSON 의 세 상태를 자동으로 매핑한다:

```json
{ "name": "민수" }                   → name = "민수", 나머지 Unset
{ "bio": null }                      → bio = Delete
{ "bio": "새로운 자기소개" }           → bio = Value("새로운 자기소개")
// bio 키 없음                       → bio = Unset
```

사용자가 Jackson 설정을 건드릴 필요 없다. 라이브러리가 Module 을 자동 등록한다.

---

## 실제 동작 확인

테스트 데이터:
```
id=1, name="철수", email="chul@test.com", nickname="CS",
phoneNumber="010-1234-5678", address="서울 강남구", bio="안녕하세요 철수입니다"
```

**테스트 1: name 만 변경**
```bash
PATCH /api/sample/members/1
{ "name": "민수" }
```
```json
{ "name": "민수", "email": "chul@test.com", "nickname": "CS",
  "phoneNumber": "010-1234-5678", "address": "서울 강남구", "bio": "안녕하세요 철수입니다" }
```
→ name 만 바뀜. 나머지 그대로.

**테스트 2: bio 명시적 삭제**
```bash
PATCH /api/sample/members/1
{ "bio": null }
```
```json
{ "name": "민수", "email": "chul@test.com", "nickname": "CS",
  "phoneNumber": "010-1234-5678", "address": "서울 강남구", "bio": null }
```
→ bio 가 null 로. `PatchField.Delete` 동작 확인.

**테스트 3: 복합 — phoneNumber 변경 + address 삭제 + bio 설정**
```bash
PATCH /api/sample/members/1
{ "phoneNumber": "010-9999-8888", "address": null, "bio": "새로운 자기소개" }
```
```json
{ "name": "민수", "email": "chul@test.com", "nickname": "CS",
  "phoneNumber": "010-9999-8888", "address": null, "bio": "새로운 자기소개" }
```
→ phoneNumber 변경, address 삭제, bio 설정. 한 요청에 세 종류 동작.

---

## 결국 뭘 만든 건가

돌아보면 이 라이브러리는 **JsonNullable + MapStruct 가 합쳐진 형태**다.

| 기능 | 출처 |
|------|------|
| 3 상태 표현 (skip / update / delete) | JsonNullable 의 아이디어 |
| 자동 생성 + DI 패턴 | MapStruct 의 형태 |
| **도메인 메서드 호출 (setter 우회 X)** | **이 라이브러리만의 차별점** |

JsonNullable 은 3 상태를 표현하지만 보일러플레이트를 사용자에게 떠넘겼다. MapStruct 는 자동 생성을 하지만 setter 를 강제했다. 이 라이브러리는 **두 도구의 장점을 합치면서, 도메인 메서드 호출이라는 한 가지를 더 지킨다.**

도메인 메서드를 호출한다는 건 곧 **도메인 불변식이 보존**된다는 뜻이다. 도메인 메서드 안에 있는 검증, 비즈니스 규칙, 상태 전이 로직이 PATCH 에서도 그대로 동작한다. setter 로 우회하는 순간 이 보장이 깨지는데, 우리는 깨지지 않는다.

---

## 실제 배포 + 적용

어노테이션 프로세서 구현을 완료하고, JitPack 으로 배포했다. 실제 프로젝트에서 의존성을 추가하고 빌드하면 AP 가 patcher 를 자동 생성한다.

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.dudxor4587:patchable:v0.1.0'
    annotationProcessor 'com.github.dudxor4587:patchable:v0.1.0'
}
```

DTO 에 `@PatchOf` 만 붙이면 컴파일 타임에 아래와 같은 patcher 가 자동 생성된다:

```java
@Component
public class MemberProfilePatchPatcher {

    public void apply(Member target, MemberProfilePatch source) {
        target.updateMember(
                source.name() != null ? source.name() : target.getName(),
                source.email() != null ? source.email() : target.getEmail(),
                source.nickname() != null ? source.nickname() : target.getNickname(),
                resolve(source.phoneNumber(), target.getPhoneNumber()),
                resolve(source.address(), target.getAddress()),
                resolve(source.bio(), target.getBio())
        );
    }

    private static <T> T resolve(PatchField<T> field, T current) {
        if (field instanceof PatchField.Value<?> v) {
            return (T) v.value();
        } else if (field instanceof PatchField.Delete<?>) {
            return null;
        }
        return current;
    }
}
```

도메인 메서드 (`updateMember`) 를 직접 호출하고, plain 필드는 null 폴백, PatchField 필드는 3 상태 분기. 사용자가 작성할 코드는 zero.

서비스에서 DI 로 주입해서 한 줄로 사용한다:

```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberProfilePatchPatcher patcher;

    @Transactional
    public Member patch(Long id, MemberProfilePatch request) {
        Member member = repository.findById(id).orElseThrow();
        patcher.apply(member, request);
        return member;
    }
}
```

GitHub: [https://github.com/dudxor4587/patchable](https://github.com/dudxor4587/patchable)

---

## 정리

- `member.partialUpdate(...)` 는 Java 의 extension method 부재로 불가능했다
- 대신 patcher 를 DI 로 주입하는 MapStruct 와 동일한 호출 패턴이 됐다
- 호출 패턴은 같지만 **안에서 일어나는 일이 다르다** — setter 가 아니라 도메인 메서드
- PatchField 로 3 상태 (skip / update / delete) 를 표현한다
- 도메인 불변식이 PATCH 에서도 그대로 동작한다

1편에서 "왜 Java 만 이러나" 라고 물었고, 2편에서 "어떻게 풀어야 하나" 라고 고민했고, 3편에서 만들어봤다.

완벽하지 않다. 하지만 **도메인 메서드를 보존하면서 PATCH 보일러플레이트를 없애는 것** — 이 라이브러리의 원래 목적 — 은 달성했다.
