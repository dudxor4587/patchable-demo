package com.patchabledemo.member.patch;

public record MemberPatchRequest(
        String name,
        String email,
        String nickname,
        String phoneNumber,
        String address,
        String bio
) {
}
