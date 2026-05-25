## 배경

Spring Boot 프로젝트에서 프로필 수정 API를 만들 일이 생겼다.

REST 시맨틱대로라면 `PATCH /members/{id}` 가 맞다. PUT 은 리소스 전체 교체고, 부분 수정은 PATCH 다. 그래서 이렇게 짰다.

```java
@Entity
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String nickname;
    private String phoneNumber;
    private String address;
    private String bio;

    public void updateMember(String name,
                             String email,
                             String nickname,
                             String phoneNumber,
                             String address,
                             String bio) {
        this.name = name;
        this.email = email;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.bio = bio;
    }
}
```

```java
public record MemberPatchRequest(
        String name,
        String email,
        String nickname,
        String phoneNumber,
        String address,
        String bio
) {}
```

```java
@Service
@RequiredArgsConstructor
public class PatchMemberService {

    private final MemberRepository repository;

    @Transactional
    public Member patch(Long id, MemberPatchRequest request) {
        Member member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        member.updateMember(
                request.name() != null ? request.name() : member.getName(),
                request.email() != null ? request.email() : member.getEmail(),
                request.nickname() != null ? request.nickname() : member.getNickname(),
                request.phoneNumber() != null ? request.phoneNumber() : member.getPhoneNumber(),
                request.address() != null ? request.address() : member.getAddress(),
                request.bio() != null ? request.bio() : member.getBio()
        );

        return member;
    }
}
```

동작은 한다. "요청에 없는 필드는 기존 값을 유지, 있는 필드만 덮어쓴다" 라는 정책이 충실히 구현돼 있다.

그런데 이걸 매번 쓰고 싶지는 않다.

---

## 이 코드의 진짜 문제

겉보기엔 멀쩡하지만 다시 보면 거슬리는 지점이 명확히 있다.

**1. 필드 N개 = `?:` N줄.** 위 코드에는 삼항이 6번 나온다. 필드가 늘어나면 그대로 늘어난다.

**2. 새 필드를 추가할 때마다 두 곳을 동시에 고쳐야 한다.** `Member.updateMember()` 의 시그니처와 `PatchMemberService.patch()` 의 `?:` 라인. 한쪽만 고치면 컴파일은 통과하는데 의미만 어긋나는 함정이 쉽게 생긴다.

**3. 도메인 메서드가 "전체 필드를 받는" 모양이 강제된다.** 진짜 의도는 "변경된 필드만 반영" 인데, 우리는 그걸 표현할 방법이 없어서 모든 필드를 다 넘기고 안에서 덮어쓴다. 사실상 `updateMember` 가 `replaceMember` 처럼 동작한다.

**4. 결국 PUT 이 쓰고싶어진다.** 부분 수정을 위해 PATCH 를 선택했는데, 정작 서비스 레이어에서는 "기존 값 + 새 값" 을 매번 머지해서 "전체 교체" 형태로 도메인 메서드에 넘기고 있다. 어차피 전체를 넘길 거면 그냥 PUT 으로 받고 클라이언트가 전체를 보내게 하는 게 깔끔하지 않은가? 라는 의문이 생긴다.

물론 내가 너무도 깔끔한 코드를 좋아하기에 과도하게 민감한 것일 수도 있다.
하지만 나와 비슷한 스타일을 가진 사람들이 있다면 충분히 공감할 부분이라고 생각한다.

---

## 다른 언어는 어떻게 푸나

여기까지 읽으면서 "원래 그런 거지 뭐" 라고 생각할 수도 있다. 그런데 다른 언어 / 프레임워크가 이 문제를 어떻게 다루는지 보면 생각이 바뀐다.

**Python — Pydantic + DRF**

Pydantic 모델에 `model_dump(exclude_unset=True)` 한 줄이면 끝난다. "사용자가 실제로 명시한 필드만" 골라서 dict 로 뽑아주기 때문이다.

```python
class MemberPatch(BaseModel):
    name: str | None = None
    email: str | None = None
    bio: str | None = None

def patch_member(id: int, req: MemberPatch):
    member = repo.get(id)
    for field, value in req.model_dump(exclude_unset=True).items():
        setattr(member, field, value)
```

Django REST Framework 는 더 노골적이다. 시리얼라이저에 `partial=True` 플래그 하나가 PATCH 의 정체성이다.

```python
serializer = MemberSerializer(instance, data=request.data, partial=True)
serializer.is_valid()
serializer.save()   # 들어온 필드만 업데이트
```

**Ruby on Rails — 그냥 된다**

Rails 는 처음부터 됐다. ActiveRecord 의 `update` 자체가 부분 업데이트의 기본형이다.

```ruby
Member.find(id).update(member_params)
# member_params 에 들어온 키만 업데이트. 안 들어온 건 그대로.
```

Strong Parameters (`params.permit(:name, :email)`) 가 입력 화이트리스트를 잡고, ActiveRecord 가 부분 업데이트를 알아서 처리한다. 우리가 위에서 본 문제가 Rails에서는 존재하지 않는다.

**TypeScript — Prisma**

Prisma 의 `update` API 자체가 PATCH 시맨틱이다.

```typescript
await prisma.member.update({
  where: { id },
  data: { name: "민수" }   // 여기 있는 필드만, 나머지는 그대로
});
```

`data` 에 넣지 않은 필드는 안 건드린다. 명시적 null 이 필요하면 `Prisma.DbNull` 같은 별도 값을 통해 타입 시스템 안에서 표현한다.

**C# — ASP.NET Core**

Microsoft 는 RFC 6902 JSON Patch 를 표준 라이브러리에 박아두었다. 우리가 해법 3 에서 `JsonNode` 왕복까지 해가며 짤 코드가, .NET 에서는 어트리뷰트 하나에 한 줄짜리 호출이다.

```csharp
[HttpPatch("{id}")]
public IActionResult Patch(int id, [FromBody] JsonPatchDocument<Member> patchDoc)
{
    var member = _repo.Get(id);
    patchDoc.ApplyTo(member);
    _repo.SaveChanges();
    return Ok(member);
}
```

이 정도 보면 슬슬 의문이 든다. **"왜 Java 만 이러고 있지?"**

---

## Java 가 유독 이상한 구조적 이유

언어 / 생태계 단위로 보면 차이가 명확하다.

**1. `null` 이 오버로딩됐다.** JavaScript 는 `undefined`(미정의) 와 `null`(명시적 null) 이 따로 있다. Python 은 Pydantic 이 "set 됐는지 여부" 를 추적한다. Java 는 `null` 하나가 두 의미를 다 떠안는다. 그래서 `JsonNullable` 같은 박싱 타입을 따로 만들어야 한다.

**2. 옵션 타입이 1순위가 아니다.** Rust 의 `Option<T>`, Scala 의 `Option`, Elixir 의 패턴 매칭처럼 "값의 부재" 를 언어 차원에서 다루는 메커니즘이 Java 에는 없다. `Optional` 은 8 에서 추가된 반환 타입 보조 도구이고, JSON 매핑이나 필드 모델링용으로 쓰기엔 어색하다.

**3. ORM 과 도메인 메서드 문화의 충돌.** Spring / JPA 진영은 엔티티에 setter 를 두지 않고 도메인 메서드로만 변경하는 스타일이 강하다. 반면 ActiveRecord, Prisma 같은 다른 진영 ORM 은 처음부터 ORM 레벨에서 부분 업데이트를 지원해서 이 충돌 자체가 없다.

**4. record 가 불변이다.** Java 의 record 는 좋은 도구이지만 "부분만 채워진 record" 를 표현하는 게 자연스럽지 않다. 모든 필드를 채워서 생성해야 하므로, "이 필드는 안 보냈다" 를 구분하려면 결국 null 로 채워두는 수밖에 없다.

---

우리가 위에서 본 `?:` 도배는 **Java 라는 언어와 Spring 이라는 프레임워크의 결정들이 겹쳐서 만들어내는 구조적 결과물**이다. 다른 진영은 이미 해결한 문제를 Java 진영은 매번 손으로 푼다.

그래서 직접 라이브러리를 만들어보기 전에, Spring 생태계 안에 이미 있는 해법들을 하나씩 적용해봤다.

---

## 해법 1. Jackson `readerForUpdating()`

Spring Boot 가 이미 깔아주는 `ObjectMapper` 에 들어있는 빌트인 기능이다. 가장 단순해보이는 해법.

```java
@Service
@RequiredArgsConstructor
public class JacksonPatchMemberService {

    private final MemberRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Member patch(Long id, String jsonBody) throws IOException {
        Member member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        objectMapper.readerForUpdating(member).readValue(jsonBody);

        return member;
    }
}
```

`readerForUpdating(member)` 는 JSON 에 명시된 필드만 기존 객체에 덮어쓴다. 요청 본문에 없는 필드는 그대로다. `?:` 도배가 사라진다.

**한계가 명확하다.**

- **엔티티에 JSON 을 직접 매핑한다.** 컨트롤러 레이어에서 막아왔던 "DTO 거치지 않은 엔티티 직접 노출" 이 서비스 레이어로 들어오는 셈이다. 게다가 `@Id` 같은 필드까지 외부 JSON 으로 덮어쓸 위험이 생긴다 (별도 `@JsonIgnore` 필요).
- **`record` 와 호환되지 않는다.** record 는 불변이라 기존 인스턴스의 필드를 갱신하는 게 불가능하다. DTO 를 record 로 받는 요즘 스타일과 정면 충돌한다.
- **setter 또는 직접 필드 접근이 필요하다.** 도메인 메서드(`updateMember`) 를 우회한다. "엔티티 변경은 항상 도메인 메서드를 통해" 라는 규칙이 깨진다.
- **JPA dirty checking 과 미묘해진다.** Jackson 이 setter 호출 → JPA 가 변경 감지 → 의도한 필드 외에 다른 게 함께 업데이트될 가능성이 있다.

**평가:** 한 줄짜리 해법이지만 잃는 게 너무 많다. 도메인 계층의 캡슐화를 일부러 뚫고 들어오는 모양이다.

---

## 해법 2. `JsonNullable<T>`

openapi-tools 의 `jackson-databind-nullable` 라이브러리가 제공하는 타입이다. `JsonNullable<T>` 는 세 가지 상태를 표현할 수 있다.

- **미포함**: `isPresent() == false`
- **명시적 null**: `isPresent() == true`, `get() == null`
- **값 있음**: `isPresent() == true`, `get() == 값`

```java
public record MemberPatchRequest(
        JsonNullable<String> name,
        JsonNullable<String> email,
        JsonNullable<String> nickname,
        JsonNullable<String> phoneNumber,
        JsonNullable<String> address,
        JsonNullable<String> bio
) {}
```

```java
@Transactional
public Member patch(Long id, MemberPatchRequest request) {
    Member member = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

    member.updateMember(
            request.name().isPresent() ? request.name().get() : member.getName(),
            request.email().isPresent() ? request.email().get() : member.getEmail(),
            request.nickname().isPresent() ? request.nickname().get() : member.getNickname(),
            request.phoneNumber().isPresent() ? request.phoneNumber().get() : member.getPhoneNumber(),
            request.address().isPresent() ? request.address().get() : member.getAddress(),
            request.bio().isPresent() ? request.bio().get() : member.getBio()
    );

    return member;
}
```

이 해법은 **PATCH 시맨틱을 정확하게 표현한다**. `{"bio": null}` 로 bio 를 명시적으로 비우는 것과 bio 자체를 안 보내는 걸 구분할 수 있다. RFC 7396 (JSON Merge Patch) 와 일치한다.

**그런데 보일러플레이트는 그대로다. 오히려 더 늘었다.**

- DTO 의 모든 필드를 `JsonNullable<T>` 로 감싸야 한다. **DTO 오염.**
- 서비스 메서드의 `?:` 는 여전히 N 줄이고, 그 안의 조건만 `!= null` 에서 `isPresent()` 로 바뀌었다.
- 외부 의존성(openapi-tools) 이 추가된다.

**평가:** PATCH 시맨틱은 살아나지만 우리가 잡으려던 문제(보일러플레이트) 는 해결되지 않는다. 이 글의 목표가 "명시적 null 표현" 이라면 정답이지만, 우리의 목표는 "PUT 처럼 자연스럽게 쓰기" 이므로 부분만 해결한 셈이다.
심지어 내가 작성한 코드와 큰 차이도 없다. 그저 `JsonNullable` 로 감싸고 `isPresent()` 체크로 바꿨을 뿐이다.
굳이 사용할 이유는 없어보인다.

---

## 해법 3. JSON Patch

또 다른 표준이다. 클라이언트가 변경 명령의 배열을 보낸다.

```json
[
  { "op": "replace", "path": "/nickname", "value": "newNick" },
  { "op": "replace", "path": "/bio", "value": "hello" }
]
```

```java
// 의존성: com.github.java-json-tools:json-patch

@PatchMapping(value = "/{id}", consumes = "application/json-patch+json")
public Member patch(@PathVariable Long id, @RequestBody JsonPatch patch)
        throws JsonPatchException, IOException {

    Member member = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

    JsonNode patched = patch.apply(objectMapper.valueToTree(member));
    Member updated = objectMapper.treeToValue(patched, Member.class);

    return repository.save(updated);
}
```

**가장 표준적이고 강력하다.** `replace`, `add`, `remove`, `move`, `copy`, `test` 까지 지원한다. PATCH 의 정수에 가장 가까운 형태다.

**그런데 우리 케이스엔 과하다.**

- **클라이언트가 명령 배열을 작성한다.** 프런트엔드 입장에서 "변경된 필드 모아서 JSON 으로 보낸다" 가 "변경 명령을 만들어서 보낸다" 로 바뀐다. 단순 폼 수정에 비해 작성 비용이 늘어난다.
- **DTO 가 사라진다.** 어떤 필드가 들어올지 명세가 코드에 드러나지 않는다. 검증/문서화가 어려워진다.
- **엔티티 ↔ JsonNode ↔ 엔티티** 왕복이 발생한다. 도메인 메서드(`updateMember`) 는 완전히 우회되고, JPA dirty checking 의 자연스러운 흐름도 깨진다.

**평가:** 보일러플레이트는 제거됐지만, DTO가 사라져버리는 것은 배보다 배꼽이 더 커지는 상황이다.
오히려 기존 코드보다 더 복잡해보인다.

---

## 해법 4. Spring `BeanUtils.copyProperties`

리플렉션 기반 유틸. "null 인 필드는 복사 대상에서 빼고 나머지만 덮어쓰기" 패턴으로 자주 보이는 방식이다.

```java
public class BeanUtilsSupport {
    public static String[] getNullPropertyNames(Object source) {
        BeanWrapper wrapped = new BeanWrapperImpl(source);
        return Stream.of(wrapped.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> wrapped.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }
}
```

```java
@Transactional
public Member patch(Long id, MemberPatchRequest request) {
    Member member = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

    BeanUtils.copyProperties(request, member, BeanUtilsSupport.getNullPropertyNames(request));

    return member;
}
```

서비스 메서드가 한 줄로 줄었다. 인상적이다.

**하지만 가장 큰 대가가 있다.**

- **타깃(`Member`) 에 setter 가 필요하다.** 우리 엔티티는 `@Getter` 만 두고 변경은 도메인 메서드로만 한다는 원칙을 지키고 있는데, 이걸 도입하려면 그 원칙을 깨야 한다. 트레이드오프가 너무 크다.
- **source 가 record 면 BeanUtils 가 잘 못 다룬다.** record 는 일반 자바빈 규약과 다르다 (`getName()` 이 아니라 `name()`). 별도 어댑터를 만들거나 record 를 포기해야 한다.
- **도메인 메서드 우회.** 어떤 필드가 어떤 의미로 바뀌었는지 도메인 레벨에서 알 수 없다.
- **Spring 에 묶인다.** `BeanUtilsSupport` 가 Spring `BeanWrapper` 를 쓴다.

**평가:** "한 줄로 줄였다" 가 매력적이지만, 그 한 줄을 위해 setter 를 다시 열어줘야 한다면 거꾸로 가는 거다.

---

## 해법 5. MapStruct `nullValuePropertyMappingStrategy = IGNORE`

컴파일 타임 코드 생성. 리플렉션 비용 없이 BeanUtils 와 동일한 효과를 낸다.

```java
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface MemberPatchMapper {
    void patch(MemberPatchRequest request, @MappingTarget Member member);
}
```

```java
@Service
@RequiredArgsConstructor
public class MapStructPatchMemberService {

    private final MemberRepository repository;
    private final MemberPatchMapper patchMapper;

    @Transactional
    public Member patch(Long id, MemberPatchRequest request) {
        Member member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        patchMapper.patch(request, member);

        return member;
    }
}
```

서비스는 한 줄, 런타임 비용 없음. **가장 정석에 가까운 해법.**

**여기서도 같은 벽에 부딪힌다.**

- **`@MappingTarget` 타깃에 setter 가 필요하다.** 도메인 메서드를 알아서 호출해주지 않는다. BeanUtils 와 같은 문제다.
- **DTO 마다 Mapper 인터페이스를 별도로 작성해야 한다.** 도메인이 늘어날수록 매퍼도 같이 늘어난다.
- **record 친화성이 부분적이다.** source 로 record 는 잘 받지만, record 를 target 으로 쓰는 패턴은 자연스럽지 않다.
- **별도 어노테이션 프로세서를 깔아야 한다.** 외부 도구 의존.

**평가:** 가장 강력한 해법이긴 한데, "엔티티에 setter 를 두지 않는다" 라는 우리 원칙과 정면 충돌한다. 이걸 쓰려면 도메인 메서드 패턴을 일부 포기해야 한다.

---

## 비교 정리

| 해법 | DTO 오염 | 외부/도구 의존 | record 친화 | 도메인 메서드 친화 | 보일러플레이트 제거 |
|------|---------|---------------|-------------|------------------|------------------|
| 단순 `?:` 분기 | 없음 | 없음 | O | O | **X** |
| Jackson `readerForUpdating` | 없음 | Jackson | X (불변 불가) | X (우회) | O |
| `JsonNullable<T>` | **있음** (필드 래핑) | openapi-tools | △ | X (분기 잔존) | **X** |
| JSON Patch | DTO 자체가 사라짐 | json-patch | — | X (우회) | O |
| `BeanUtils` | 없음 | Spring | △ | X (setter 강제) | O |
| MapStruct | 없음 | mapstruct (apt) | △ | X (setter 강제) | O |

사실 1,4,5 해법은 근본적으로 동일한 구조를 가진다.
표를 한 줄 한 줄 보면 모든 해법이 **무언가 하나씩 양보를 강요한다.**

- 보일러플레이트를 없애면 → 도메인 메서드를 우회하거나 setter 를 열어야 한다
- 도메인 메서드를 지키면 → 보일러플레이트가 그대로다 (`JsonNullable`, 단순 `?:`)
- 시맨틱을 정확히 표현하면 → DTO 가 오염된다 (`JsonNullable`)
- 표준에 가장 충실하면 → 클라이언트가 부담을 진다 (JSON Patch)

"이 둘을 동시에 만족시키는 해법은 없는가?" 가 다음 질문이 된다.

- 보일러플레이트는 사라지고
- 도메인 메서드는 그대로 호출되고
- DTO 는 그냥 평범한 `record` 이고
- 외부 의존은 최소이거나 없음
- DTO와 Entity는 서로를 모를 것

---

## 그래서 이런 라이브러리가 있으면 좋겠다

사실 실제로 거리낌 없이 사용하려면, 기존 PUT을 구현할 때처럼 딱히 불편함 없이 사용할 수 있어야된다고 생각한다.

다른 인터페이스를 거치고, 그걸 서비스에 선언해서 적용하는 방식은 오히려 사용자 경험이 더 나빠진다고 생각한다.

PUT과 동일하게 서비스는 한 줄. 추가로 도메인 메서드를 최대한 그대로 사용하여 PUT을 구현하는 전체 update와 외부 시선으로는 별 다를게 없는 라이브러리였으면 좋겠다.

웬만하면 어노테이션 하나로 동작하도록 구현하고 싶은데, 구현 단계에서 직접 겪어보며 판단할 계획이다.

---

## 전체 비교 코드

각 해법의 실제로 동작하는 예제는 이 레포의 `src/main/java/com/patchlibrary/comparison/` 아래에 해법별 패키지로 정리해두었다.

- `comparison/jackson/` — `readerForUpdating()`
- `comparison/jsonnullable/` — `JsonNullable<T>`
- `comparison/jsonpatch/` — RFC 6902 JSON Patch
- `comparison/beanutils/` — `BeanUtils.copyProperties`
- `comparison/mapstruct/` — MapStruct `IGNORE`

각 패키지는 동일한 `Member` 엔티티에 대해 동일한 PATCH 동작을 다른 방식으로 구현한 것이다. 직접 돌려보면 표의 항목들이 코드에서 어떻게 드러나는지 명확해진다.
