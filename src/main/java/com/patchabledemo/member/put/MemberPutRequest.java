package com.patchabledemo.member.put;

public record MemberPutRequest(
        String name,
        String email,
        String nickname,
        String phoneNumber,
        String address,
        String bio
) {
}
