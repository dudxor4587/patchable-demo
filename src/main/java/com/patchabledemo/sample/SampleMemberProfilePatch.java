package com.patchabledemo.sample;

import com.patchable.api.PatchField;
import com.patchable.api.PatchOf;
import com.patchabledemo.member.Member;

@PatchOf(value = Member.class, method = "updateMember")
public record SampleMemberProfilePatch(
        String name,
        String email,
        String nickname,
        PatchField<String> phoneNumber,
        PatchField<String> address,
        PatchField<String> bio
) {}
