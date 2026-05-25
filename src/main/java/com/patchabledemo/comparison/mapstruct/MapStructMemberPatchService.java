package com.patchabledemo.comparison.mapstruct;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapStructMemberPatchService {

    private final MapStructMemberRepository repository;
    private final MapStructMemberPatchMapper patchMapper;

    @Transactional
    public MapStructMember patch(Long id, MapStructMemberPatchRequest request) {
        MapStructMember member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        // 컴파일 타임에 생성된 매퍼가 member.setXxx(request.xxx()) 들을 직접 호출.
        // request 필드가 null 이면 nullValuePropertyMappingStrategy = IGNORE 에 따라 건너뜀.
        patchMapper.patch(request, member);

        return member;
    }
}
