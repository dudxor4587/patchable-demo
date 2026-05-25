package com.patchabledemo.member.patch;

import com.patchabledemo.member.Member;
import com.patchabledemo.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
