package com.patchabledemo.member.put;

import com.patchabledemo.member.Member;
import com.patchabledemo.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PutMemberService {

    private final MemberRepository repository;

    @Transactional
    public Member replace(Long id, MemberPutRequest request) {
        Member member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        member.updateMember(
                request.name(),
                request.email(),
                request.nickname(),
                request.phoneNumber(),
                request.address(),
                request.bio()
        );

        return member;
    }
}
