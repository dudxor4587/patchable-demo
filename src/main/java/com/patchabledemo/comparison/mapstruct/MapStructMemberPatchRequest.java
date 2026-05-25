package com.patchabledemo.comparison.mapstruct;

// MapStruct 는 record source 도 잘 다룬다 (name() accessor 를 인식).
// 그래서 DTO 자체는 평범한 record 로 둘 수 있다.
public record MapStructMemberPatchRequest(
        String name,
        String email,
        String nickname,
        String phoneNumber,
        String address,
        String bio
) {}
