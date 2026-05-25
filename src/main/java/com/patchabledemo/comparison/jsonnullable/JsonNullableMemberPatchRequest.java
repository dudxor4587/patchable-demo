package com.patchabledemo.comparison.jsonnullable;

import org.openapitools.jackson.nullable.JsonNullable;

// 모든 필드를 JsonNullable 로 감싼다.
// 대신 세 가지 상태를 표현 가능:
//   - 미포함:        isPresent() == false
//   - 명시적 null:    isPresent() == true && get() == null
//   - 값 있음:        isPresent() == true && get() == "값"
public record JsonNullableMemberPatchRequest(
        JsonNullable<String> name,
        JsonNullable<String> email,
        JsonNullable<String> nickname,
        JsonNullable<String> phoneNumber,
        JsonNullable<String> address,
        JsonNullable<String> bio
) {}
