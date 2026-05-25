package com.patchabledemo.sample;

import com.patchabledemo.member.Member;
import com.patchabledemo.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SamplePatchService {

    private final MemberRepository repository;
    private final SampleMemberProfilePatchPatcher patcher;

    @Transactional
    public Member patch(Long id, SampleMemberProfilePatch request) {
        Member member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        patcher.apply(member, request);

        return member;
    }
}
