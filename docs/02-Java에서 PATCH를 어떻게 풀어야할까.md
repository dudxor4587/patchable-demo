## 배경

[1편](01-Java는%20왜%20PATCH를%20배려하지%20않는가.md)에서 Java/Spring 진영의 PATCH 가 왜 유독 보일러플레이트를 강요하는지, 그리고 기존 해법 5개 (Jackson `readerForUpdating`, JsonNullable, JSON Patch, BeanUtils, MapStruct) 가 각자 무엇을 양보하는지 살펴봤다. 결론은 "어떤 해법도 완전하지 않으니 직접 만들어보겠다" 였다.

그 라이브러리를 본격적으로 만들기 전에, 디자인 단계에서 마주친 **진짜 문제들**을 글로 정리해보려 한다.

---

## 다시 짚어보는 진짜 목표

1편 끝에 "이런 라이브러리가 있으면 좋겠다" 라고 적은 조건들을 정리하면 다섯 개다.

1. **보일러플레이트 제거** — 1편 첫 코드의 `?:` 도배가 사라질 것
2. **도메인 메서드 보존** — 엔티티의 setter 를 우회하지 않고, 사용자가 작성한 도메인 메서드 (`updateMember` 류) 가 그대로 호출될 것
3. **외부 의존 최소** — DTO 가 평범한 record 일 것. 별도 박싱 타입 (`JsonNullable<T>`) 같은 게 침투하지 않을 것
4. **사용성** — 서비스 코드가 PUT 만큼 자연스러울 것. "한 줄로 호출 가능" 수준
5. **DTO 와 Entity 는 서로를 모를 것** — DDD 의 의존 방향이 깨지지 않을 것

다섯 개를 동시에 만족시키는 게 목표인데, 디자인을 그려볼수록 문제가 계속 발생했다. 그게 이 글의 본론이다.

---

## 잠깐, 다른 언어들은 정말 깔끔한가

1편에서 Python, Rails, Prisma, ASP.NET Core 가 PATCH 를 자연스럽게 다룬다고 적었는데, 라이브러리 디자인을 본격적으로 시작하니까 그 비교가 좀 다르게 보였다. 솔직하게 다시 보자.

**Python (Pydantic + DRF)**

```python
for field, value in req.model_dump(exclude_unset=True).items():
    setattr(member, field, value)
```

`setattr` 은 **도메인 메서드 우회**다. 우리가 1편에서 BeanUtils, MapStruct 를 깠던 그 이유 (setter 우회) 와 같은 짓을 한다.

**Rails**

```ruby
Member.find(id).update(member_params)
```

`update` 는 ActiveRecord 메서드인데 DB 컬럼에 직접 매핑한다. Rails 의 모델은 도메인 + 영속성 + 직렬화가 한 클래스에 섞인 **Active Record 패턴**이지, DDD 의 순수 도메인 객체가 아니다.

**Prisma (TypeScript)**

```typescript
await prisma.member.update({ where: { id }, data: { name: "민수" } });
```

`prisma.member.update` 는 데이터베이스 계층 호출이다. 모델은 보통 `interface Member { ... }` 같은 anemic data 객체이고, 비즈니스 로직은 함수 / 서비스 레이어에 흩어져 있다.

**ASP.NET Core**

```csharp
patchDoc.ApplyTo(member);
```

`ApplyTo` 는 리플렉션으로 필드/setter 를 직접 건드린다. **도메인 메서드 우회**. 1편 해법 3 (JSON Patch) 에서 우리가 했던 그 짓이 Microsoft 가 표준화해서 깨끗해 보일 뿐.

---

**그러니까 정직한 일반화는 이거다.**

다른 언어들이 "PATCH 를 자연스럽게 푸는" 방식은 다음 셋 중 하나다:

| 전략 | 대표 |
|------|------|
| 도메인 자체가 anemic (데이터 통) | Prisma, Python 의 일반적 사용 |
| Active Record 로 도메인 + 영속성 통합 | Rails |
| 도메인 메서드 우회 (reflection/setter) | ASP.NET PatchDocument, Pydantic setattr |

다 우리가 1편에서 안된다고 선을 그은 것들이다. 다른 언어가 쉬워 보이는 이유는 그들이 **풍부한 도메인 모델을 처음부터 포기했기 때문**이지, "도메인이 PATCH를 모른다" 가 진실인 게 아니다.

그러니까 이 라이브러리는 **다른 언어를 베끼는 게 아니라, 다른 진영이 안 풀고 우회한 진짜 문제를 정면으로 푸는 시도**다. 어쩌면 양보 없이는 풀 수 없는 문제일 수도 있다.

---

## 디자인 공간 — 후보들

다섯 조건을 다 살리려고 그려본 디자인은 크게 다섯 갈래로 갈렸다. 각자 무엇을 양보하는지 살펴보자.

### 후보 1. DTO 에 어노테이션

```java
@Patchable(target = Member.class)
public record MemberPatchRequest(String name, String email, String bio) {}

// 사용:
member.applyPatch(request);
```

가장 깔끔한 사용 API. 하지만 `target = Member.class` 가 등장하는 순간 **DTO 가 Entity 를 안다**. 조건 5가 깨진다.

### 후보 2. Entity 에 어노테이션 + DTO 명시

```java
@Patchable(source = MemberPatchRequest.class, via = "updateMember")
public class Member { ... }

// 사용:
member.applyPatch(request);
```

이번엔 **Entity 가 DTO 를 안다**. 도메인이 application 계층의 객체 (`MemberPatchRequest`) 를 알게 되는 거라, 도메인 레이어가 외부 계층의 모양을 알게 된다는 것에서 더 문제다.

### 후보 3. Patcher 인터페이스 (양방향 격리)

```java
// DTO: 의존성 zero
public record MemberPatchRequest(String name, String email, String bio) {}

// Entity: 의존성 zero
public class Member {
    public void updateMember(String name, String email, String bio) { ... }
}

// 사용자가 인터페이스 하나만 선언
@Patcher
public interface MemberPatcher {
    void updateMember(Member target, MemberPatchRequest source);
}

// 사용:
memberPatcher.updateMember(member, request);
```

DTO 와 Entity 가 서로를 완전히 모른다. 라이브러리에 대한 의존은 **다리 (Patcher) 한 곳에만** 집중된다.

대신 사용자가 **인터페이스를 하나 더 만들어야 하고**, 서비스 코드는 `memberPatcher.updateMember(member, request)` 가 된다. 한 줄이긴 하지만 PUT 의 `member.updateMember(...)` 와 모양이 살짝 다르다.
"member 의 메서드를 호출하는 게 아니라 patcher 라는 다른 객체를 거치는 모양." 사용성이 한 단계 낮아진다.
사실 나같아도 불편해서 안쓸 것 같다.

### 후보 4. Entity 어노테이션 + 자동 생성 메서드

```java
@Patchable
public class Member {
    public void updateMember(String name, String email, String bio) { ... }

    // 라이브러리가 자동 생성:
    // public void partialUpdate(String name, String email, String bio) {
    //     updateMember(
    //         name != null ? name : this.name,
    //         email != null ? email : this.email,
    //         bio != null ? bio : this.bio
    //     );
    // }
}

// 사용:
member.partialUpdate(request.name(), request.email(), request.bio());
```

Entity 의 시그니처에 DTO 타입이 등장하지 않는다 (`String name, String email, String bio` 를 받을 뿐). 후보 2 의 의존 방향 문제는 사라진다. 그리고 사용자 코드는 PUT 의 `member.updateMember(...)` 와 거의 같은 모양이 된다.

대신 다른 문제가 생긴다. **자동 생성된 `partialUpdate` 안에 "null 이면 스킵" 이라는 로직이 들어간다**. 이건 PATCH 의 시맨틱이다. 도메인 클래스 안에 PATCH 라는 protocol 의 규약이 박히는 거다.

극단적으로 말하면 **도메인이 HTTP 를 알게 되는 셈**이다.

이건 이전 후보들보다 좋지 않은 양보지만, 그래도 도메인과 DTO 가 서로를 모르는 건 유지된다. 그리고 사용자가 작성할 별도 코드도 없다. 자동 생성이 잘 되면 UX 도 PUT 과 거의 같아진다.

그런데 정말 이게 'HTTP 의존' 인지는 한 번 더 따져볼 가치가 있어 보인다. 뒤에서 다시 짚어볼 필요가 있을 것 같다.

### 후보 5. 정적 유틸

```java
// Entity: 완전 순수
@Patchable(via = "updateMember")
public class Member {
    public void updateMember(String name, String email, String bio) {
        Objects.requireNonNull(name);
        // ...
    }
}

// 라이브러리가 별도 클래스로 자동 생성:
// public final class MemberPatchOps {
//     public static void apply(Member target, MemberPatchRequest source) {
//         target.updateMember(
//             source.name() != null ? source.name() : target.getName(),
//             source.email() != null ? source.email() : target.getEmail(),
//             source.bio() != null ? source.bio() : target.getBio()
//         );
//     }
// }

// 사용:
MemberPatchOps.apply(member, request);
```

후보 3 (Patcher 인터페이스) 의 변형. 사용자가 직접 인터페이스를 작성하지 않고, 라이브러리가 정적 유틸을 자동 생성한다. Spring 빈 주입도 필요 없다.

도메인은 후보 3 만큼 깨끗하고, 사용자가 작성할 인터페이스도 없다. 양보한 건 단 하나, **`member.무엇()` 신택스 대신 `MemberPatchOps.apply(member, ...)` 라는 정적 호출 모양**.

---

## 비교표 — 어디서 양보하는지

| 후보 | DTO 가 Entity 알게 됨 | Entity 가 DTO 알게 됨 | 도메인이 HTTP 시맨틱 알게 됨 | 사용자가 작성할 별도 코드 | UX (사용 모양) |
|------|----|----|----|----|----|
| 1. DTO 에 어노테이션 | **❌** | ✅ | ✅ | 없음 | `member.applyPatch(request)` |
| 2. Entity 에 어노테이션 (source 명시) | ✅ | **❌** | ✅ | 없음 | `member.applyPatch(request)` |
| 3. Patcher 인터페이스 | ✅ | ✅ | ✅ | 인터페이스 1개 | `patcher.updateMember(m, r)` |
| 4. Entity 어노테이션 + 자동 생성 | ✅ | ✅ | **❌** | 없음 | `member.partialUpdate(...)` |
| 5. 정적 유틸 | ✅ | ✅ | ✅ | 없음 | `MemberPatchOps.apply(m, r)` |

**모든 후보가 정확히 하나씩 양보한다.** 어느 칸도 모두 ✅ 일 수 없다. 이게 이 글의 가장 중요한 발견이다.

---

## 판단의 충돌

다섯 조건 사이에 어떤 부분들이 부딪히는지 정리하면 이렇다.

**UX vs 도메인 순수성.** 사용자 코드를 `member.뭐()` 처럼 가장 자연스럽게 만들려면 그 메서드가 Member 안에 존재해야 한다. 그 순간 Member 가 PATCH 시맨틱이든 DTO 타입이든 무언가를 알게 된다. 도메인을 완전히 깨끗하게 두려면 호출이 한 단계 더 멀어진다.

**격리 vs 자연스러움.** DTO 와 Entity 가 서로를 모르는 양방향 격리는 깔끔하다. 하지만 그 격리를 위해서는 "다리" 가 필요하고, 그 다리는 사용자 코드에 한 단계의 불편함 을 추가한다.

---

## 그래서 어떻게 풀까

여기서 정직하게 인정해야 할 게 있다. **다섯 조건을 다 만족시키는 디자인은 없다.**

가장 사용성이 좋은 것은 4번이기에, 4번에 대해 더 파보자.

나는 "null이면 스킵"이라는 로직이 HTTP PATCH라는 프로토콜의 시맨틱이라고 적었는데, 정말 그게 맞는걸까?

생각해보면 "null이면 스킵"이라는 규약은 PATCH 라는 프로토콜의 시맨틱이 아니라고도 볼 수 있다.

애초에 PATCH 요청에선 "이 필드는 업데이트 대상에서 제외" 라는 의미로 null 을 보내는 게 표준이 아니다. 그냥 "값이 없으면 null" 이라고 매핑을 하는 것 뿐이다.

그래서 일단 4번 방향으로 잠정 결정하고 더 파보기로 했다.

---

## 그런데 한 가지 더 — 명시적 삭제

후보 4 로 마음이 기울던 와중에 빈 칸이 하나 보였다. **명시적으로 필드를 null 로 만들고 싶을 때는 어떻게 표현하지?**

지금 우리 약속 (`null = 미지정 = 스킵`) 에서는 클라이언트가 `{"bio": null}` 을 보내도 "안 바꿈" 으로 해석된다. 그러면 "bio 를 비워주세요" 라는 의도를 표현할 길이 없다.

처음엔 sentinel 문자열을 떠올렸다. `bio = "DELFLAG"` 같은 약속.

```java
{ "bio": "DELFLAG" }   // 클라이언트
// 서버: bio 가 "DELFLAG" 면 null 로 설정
```

곰곰이 보니 함정이 여러 개다. String 필드에만 동작한다 (Integer, LocalDate 는?). 누가 진짜로 그 문자열을 자기소개에 쓰면 자기 bio 가 사라진다. 매직 스트링은 일반적으로 안티 패턴이다.

다음으로 어노테이션을 떠올렸다.

```java
public record MemberPatchRequest(
    String name,
    @Deletable String bio    // ← 이 필드의 null 은 "삭제 의도"
) {}
```

이러면 사용자가 필드 단위로 의도를 표시할 수 있어서 더 깔끔해 보였다. 그런데 한 단계 더 들어가니 진짜 문제가 보였다.

**Jackson 이 두 상태를 한 상태로 합쳐버린다.**

JSON 차원에서는 두 가지가 명확히 다르다:

```json
case 1:   { "name": "민수" }                  ← bio 키가 없음 (skip 의도)
case 2:   { "name": "민수", "bio": null }     ← bio 가 명시적 null (delete 의도)
```

근데 Jackson 의 기본 동작은 두 경우 다 record 의 `bio` 를 `null` 로 채운다:

```java
// case 1 의 결과:  MemberPatchRequest(name="민수", bio=null)
// case 2 의 결과:  MemberPatchRequest(name="민수", bio=null)   ← 같음
```

`@Deletable` 어노테이션을 붙여도 이 정보 손실은 안 풀린다. 어노테이션은 "이 필드의 null 을 어떻게 해석할지" 의 규칙일 뿐, 어떤 case 로 들어왔는지는 그 시점에 이미 사라져 있다.

이게 **Java/Spring 진영에서 PATCH 가 풀리지 않는 진짜 함정**이다. 다른 언어들은 이 함정을 겪지 않는다:

- JavaScript: `undefined` (미지정) 와 `null` (명시적 null) 이 언어 차원에서 다른 값
- Python: Pydantic 의 `exclude_unset` 가 "set 됐는지 여부" 를 추적

Java 만 이 정보를 디시리얼라이즈 시점에 잃는다. **1편에서 본 `JsonNullable<T>` 가 정확히 이 함정을 풀려고 만들어진 도구**다:

```java
JsonNullable<String> bio;

// case 1:  bio.isPresent() == false                       (키 없음)
// case 2:  bio.isPresent() == true && bio.get() == null   (명시적 null)
// case 3:  bio.isPresent() == true && bio.get() == "값"   (값 있음)
```

이렇게 보면 1편에서 JsonNullable 을 "DTO 오염" 으로 깐 평가가 살짝 정밀해진다. JsonNullable 은 단순히 보일러플레이트를 늘리는 도구가 아니라, **명시적 삭제를 표현하는 거의 유일한 길**이다. 우리는 그 비용 (DTO 오염) 을 받아들이지 못해서 그것 없이 사는 라이브러리를 만들려는 것이고, **그 과정에서 명시적 삭제는 양보**하게 되는 셈이다.

---

## 한 발 더 들어가면

여기서 멈추면 "명시적 삭제는 양보" 가 결론이다. 근데 1편 의 JsonNullable 비판을 다시 한 번 정직하게 뜯어보면 한 가지가 더 보인다.

1편의 비판은 두 가지였다:
- 모든 필드를 JsonNullable 로 감싸야 한다 — **DTO 오염**
- 서비스 메서드의 `?:` 는 여전히 N 줄 — **보일러플레이트 잔존**

두 번째는 사실 **라이브러리 부재의 문제**다. JsonNullable 자체의 결함이 아니라, 그 위에서 동작할 라이브러리가 없어서 사용자가 매번 `isPresent() / get()` 으로 구분해서 작성해야 했던 것. 우리가 만드는 라이브러리는 그 코드를 자동 생성한다.

남는 건 첫 번째 — "DTO 에 wrapping 이 있다" 라는 모양 자체. 이건 수학적으로 피할 수 없다 (3 상태 표현하려면 3 상태 컨테이너 필요). 하지만 **wrapping 의 적용 범위를 합리적으로 좁힐 수 있다.**

---

## 도메인의 nullability 가 wrapping 을 결정한다

모든 필드가 wrapping 이 필요한 게 아니다. 어떤 필드는 명시적 삭제가 의미 자체로 성립하지 않는다.

도메인 모델에서 **NOT NULL 인 필드** — 이름, 이메일 같은 필수 정보 — 는 본질적으로 "비우기" 가 의미 없다. 도메인 규약상 그 필드는 항상 존재해야 한다. 그러니 PATCH 로도 비울 수 없는 게 맞고, wrapping 도 필요 없다.

도메인 모델에서 **Nullable 인 필드** — 자기소개, 전화번호 같은 옵셔널 정보 — 는 null 이 도메인적으로 valid 한 상태다. "비우기" 가 의미 있는 작업이고, 그 의도를 표현하려면 wrapping 이 필요하다.

**도메인의 nullability 가 DTO 의 wrapping 결정을 그대로 좌우한다.** 우연이 아니라 의미상 일관성.

| Entity 필드 | DTO 표현 | 가능한 상태 |
|---|---|---|
| 필수 (NOT NULL) | 평범한 타입 | 2 상태 (skip / update) |
| 옵셔널 (Nullable) | `PatchField<T>` | 3 상태 (skip / update / delete) |

코드로 보면 이렇게 된다:

```java
@Patchable
public class Member {
    private String name;                          // NOT NULL
    private String email;                         // NOT NULL
    @Column(nullable = true)
    private String bio;                           // Nullable
    @Column(nullable = true)
    private String phoneNumber;                   // Nullable
}

public record MemberPatchRequest(
    String name,                                  // 평범 (필수에 대응)
    String email,                                 // 평범
    PatchField<String> bio,                       // wrapping (옵셔널에 대응)
    PatchField<String> phoneNumber                // wrapping
) {}
```

`PatchField<T>` 는 라이브러리가 제공하는 sealed 타입이다:

```java
public sealed interface PatchField<T> {
    record Unset<T>()        implements PatchField<T> {}  // 미지정 → skip
    record Value<T>(T value) implements PatchField<T> {}  // 값 설정 → update
    record Delete<T>()       implements PatchField<T> {}  // 비우기 → delete
}
```

세 동사가 세 의도와 정확히 대응된다. 자동 생성된 patcher 안에서는 패턴 매칭으로 깨끗하게 갈라진다:

```java
// 라이브러리가 자동 생성:
switch (request.bio()) {
    case PatchField.Unset<String>()       -> { /* skip */ }
    case PatchField.Value<String>(var v)  -> member.updateBio(v);
    case PatchField.Delete<String>()      -> member.clearBio();
}
```

JsonNullable 의 `isPresent() && get() == null` 같은 우회적 분기 없이, 세 의도가 한 번에 갈라진다. Java 21 의 sealed + pattern matching 이 컴파일러 차원에서 빠뜨림을 막아준다.

---

## 와이어 포맷과 라이브러리 검증

와이어에서는 이렇게 보인다:

```json
// 필수 필드 (평범한 타입):
{ "name": "민수" }      → name 수정
// name 키 없거나 null → skip

// 옵셔널 필드 (PatchField):
{ "bio": "안녕" }       → bio 수정
{ "bio": null }         → bio 삭제
// bio 키 없음          → skip
```

같은 와이어 모양 (`{"field": null}`) 이지만 필드 종류에 따라 의미가 다르다 — `name` 의 null 은 skip, `bio` 의 null 은 삭제. 처음엔 어색해 보이지만 **도메인의 nullability 를 그대로 비춘 결과**다. OpenAPI 문서에는 두 필드의 타입 (`String` vs `PatchField<String>`) 이 다르게 표시되니까 API 소비자도 헷갈리지 않는다.

그리고 이 규칙이 일관되게 지켜지는지 **라이브러리가 컴파일 타임에 검증**할 수 있다:

- Entity 의 필드가 NOT NULL 인데 DTO 가 `PatchField` 로 감싸면 → 컴파일 에러
- Entity 의 필드가 Nullable 인데 DTO 가 평범한 String 으로 받으면 → 경고 (명시적 삭제 불가)

사용자는 Entity 의 nullability 만 정확히 표시하면 된다. 나머지는 라이브러리가 가이드한다.

---

## 라이브러리는 매핑을 어떻게 아나

"컴파일 타임에 검증한다" 라고 했는데, 그러려면 어노테이션 프로세서가 **어떤 DTO 가 어떤 Entity 의 PATCH 인지** 를 알아야 한다. 어떻게?

이게 단순하지 않은 이유는 **같은 Entity 에 대해 여러 DTO 가 존재하는 게 일반적**이기 때문이다.

```java
@Patchable
public class Member { ... }

// 일반 사용자 프로필 수정
public record MemberProfilePatch(String nickname, PatchField<String> bio) {}

// 결제 정보 수정 (별도 권한)
public record MemberPaymentPatch(PatchField<String> cardNumber) {}

// 관리자 권한 변경
public record AdminMemberPatch(String role, PatchField<LocalDate> bannedUntil) {}
```

다 같은 Member 를 PATCH 하지만 모양이 다르다. 라이브러리는 셋 다 patcher 코드를 만들어줘야 한다.

매핑을 알 수 있는 방식들을 따져봤다:

- **이름 컨벤션** — `<Entity>Patch...` 패턴으로 매칭. 여러 DTO 시나리오에서 매칭 모호.
- **자동 DTO 생성** — Entity 만 보고 라이브러리가 DTO 를 만들어줌. 사용자가 DTO 모양을 컨트롤 못 함 (필드 선택, validation 등 다 불가능). 실용성 없음.
- **별도 매핑 클래스** — 우리가 거부한 Patcher 인터페이스의 다른 모양.
- **DTO 측 어노테이션** — `@PatchOf(Member.class)`. DTO 가 자기가 어떤 Entity 의 PATCH 인지 선언.

마지막이 유일하게 유연하고 명시적인 길이다.

```java
@PatchOf(Member.class)
public record MemberProfilePatch(
    String nickname,
    PatchField<String> bio
) {}
```

---

## "DTO 는 도메인을 모른다" 의 정확한 의미

`@PatchOf(Member.class)` 를 쓰는 순간 DTO 파일에는 `Member` 가 두 번 등장한다 — `import` 한 줄과 어노테이션 한 줄. 1편에서 선언한 격리 원칙 ("DTO 와 Entity 는 서로를 모를 것") 을 깨는 건가?

원칙의 두 가지 강도를 구분해야 한다.

**강한 해석**: DTO 소스 파일에 Entity 이름이 0번 등장.
**약한 해석**: DTO 가 Entity 의 행동 / 구조에 의존하지 않음. DTO 의 메서드 / 필드 / 런타임 동작이 Entity 와 무관.

강한 해석을 채택하면 어떤 라이브러리도 만족 못 시킨다. 실제 Spring Boot DTO 는 이미 Jackson, Jakarta Validation, Spring 같은 외부 라이브러리를 한가득 import 한다. "도메인 클래스 한 줄만 import 금지" 는 일관성 없는 원칙이다.

약한 해석으로 보면 `@PatchOf` 는 원칙을 안 깬다:

- DTO 의 메서드 / 필드는 평범한 record. Entity 타입이 시그니처에 등장 안 함.
- 어노테이션 retention 을 `SOURCE` 로 두면 컴파일 후 바이트코드에서 사라짐. 런타임 의존 zero.
- DTO 가 Entity 의 메서드를 호출하지 않음. 도메인 로직을 수행하지 않음.
- 의존의 본질은 "라이브러리에게 의도를 알려주는 메타데이터" 일 뿐.

1편에서 진짜로 막으려던 건 "DTO 가 도메인 메서드를 호출하거나 도메인 로직을 수행" 이었지, "import 한 줄도 안 쓴다" 가 아니었다. 약한 해석이 그 정신을 정확히 보존한다.

그래서 `@PatchOf` 를 받아들이기로 한다. 다른 모든 zero-import 옵션들이 더 큰 비용을 가져왔다.

---

## 필드 이름이 일치하지 않는다면

DTO 와 Entity 가 매핑된다고 알려준 다음, 라이브러리는 어떻게 DTO 의 어떤 필드를 도메인 메서드의 어떤 파라미터와 짝지을까?

기본 답: **이름 매칭.**

```java
@Patchable
public class Member {
    public void updateMember(String name, String bio) { ... }
}

@PatchOf(Member.class)
public record MemberProfilePatch(
    String name,     // → 도메인 메서드의 name 파라미터에 매칭
    String bio       // → bio 파라미터에 매칭
) {}
```

이름이 같으면 자동 매칭. 다르면 **컴파일 에러.**

```java
@PatchOf(Member.class)
public record MemberProfilePatch(
    String displayName,   // ← 도메인 메서드에 displayName 없음
    String bio
) {}
// 컴파일 에러: "displayName 에 매칭되는 파라미터가 Member 의 도메인 메서드에 없습니다"
```

근데 실무에서는 이름을 다르게 두고 싶은 경우가 있다 — API 명세는 `display_name` 인데 도메인은 `name` 이라거나, 클라이언트가 쓰는 이름과 도메인이 쓰는 이름이 다르다거나.

이걸 풀려고 라이브러리에 `@MapTo` 같은 매핑 어노테이션을 도입할 수도 있다. 근데 한 단계 더 들어가면 이게 라이브러리가 풀어야 할 문제가 아니라는 게 보인다.

**라이브러리는 `@PatchOf` 가 붙은 어떤 DTO 든 받을 수 있다.** 도메인 메서드 파라미터와 이름이 일치하는 PatchDTO 만 만들어주면 그 다음을 자동화한다. 이건 강한 제약이 아니라 책임 범위를 정확히 그어주는 거다.

그리고 명확한 의존성 분리를 하려면 결국 **presentation 레이어의 RequestDTO 와 application 레이어의 PatchDTO 가 다른 객체**여야 한다. 그 둘 사이에 converter 가 들어가서 이름을 도메인 쪽으로 맞춰주면, 라이브러리는 그 converter 가 만들어낸 PatchDTO 를 받아서 일한다.

```java
// Presentation 레이어 — 외부 API 명세에 맞춘 RequestDTO
public record MemberProfileUpdateRequest(
    String displayName,
    PatchField<String> bio        // 3 상태가 wire 에서 캡처되는 자리
) {}

// Converter — presentation → application. 이름만 도메인 쪽으로 맞춰줌.
@Component
class MemberProfileRequestConverter {
    public MemberProfilePatch convert(MemberProfileUpdateRequest req) {
        return new MemberProfilePatch(req.displayName(), req.bio());
        //                            ^^^^^^^^^^^^^^^^^  ^^^^^^^^^
        //                            이름 매핑           PatchField 그대로 통과
    }
}

// Application 레이어 — 라이브러리가 받는 PatchDTO. 필드 이름이 도메인 메서드 파라미터와 일치.
@PatchOf(Member.class)
public record MemberProfilePatch(
    String name,
    PatchField<String> bio
) {}
```

`PatchField<T>` 가 두 DTO 모두에 등장한다. 이건 의존성 누수가 아니라 라이브러리의 정상 작동 표면이다 — 3 상태 정보가 JSON 디시리얼라이제이션 시점에 태어나니까 RequestDTO 부터 wrapping 이 필요하고, converter 는 그걸 통과시키기만 한다. Jackson 의 `@JsonProperty` 가 DTO 에 등장하는 것과 같은 종류의 의존이다.

라이브러리에 매핑 어노테이션을 도입하지 않는다. 이름이 다르면 DTO 를 변환하는 쪽 (presentation converter) 이 매핑하고, 라이브러리는 도메인 메서드 파라미터와 정합된 PatchDTO 만 받는다.

---

## 남는 비용들

- **`PatchField<T>` 가 DTO 에 등장한다** — 의미 있는 의존이다 — 3 상태 표현이 필요한 곳에만, 도메인의 nullability 에 따라 자동으로 결정.
- **`@PatchOf(Member.class)` 가 DTO 에 등장한다** — 컴파일 타임 hint. SOURCE retention 으로 런타임에 사라짐. 격리 원칙의 약한 해석을 받아들임.
- **와이어의 null 의미가 필드마다 다른 미묘함** — 문서로 보완.
- **Entity 의 nullability 를 라이브러리가 어떻게 인식하나** — JPA `@Column(nullable=false)`, DTO `@NonNull`, 또는 별도 어노테이션.
- **필드 이름이 다르면 converter 가 필요** — 라이브러리는 이름 매핑을 떠안지 않는다. 그 책임은 presentation 레이어의 converter 에 있고, 라이브러리는 도메인 메서드 파라미터와 정합된 PatchDTO 만 받는다.

이렇게 정의하면 보일러플레이트는 사라지고, 도메인 메서드는 그대로 호출되고, 명시적 삭제도 표현 가능하고, DTO 는 도메인 nullability 를 거울처럼 반영하면서, 사용자 코드는 PUT 만큼 짧고, **컴파일 타임 정합성까지 보장된다**.

완벽한 답은 아니지만, 양보의 위치가 **무작위가 아니라 도메인 모델의 자연스러운 경계와 일치한다**.
