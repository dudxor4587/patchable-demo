package com.patchabledemo.comparison.jsonnullable;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JsonNullableMemberPatchService {

    private final JsonNullableMemberRepository repository;

    // 도메인 메서드는 그대로 호출되지만, 분기 보일러플레이트는 그대로 (오히려 글자 수가 늘었다).
    @Transactional
    public JsonNullableMember patch(Long id, JsonNullableMemberPatchRequest request) {
        JsonNullableMember member = repository.findById(id)
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
}
