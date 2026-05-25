package com.patchabledemo.comparison.beanutils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BeanUtilsMemberPatchService {

    private final BeanUtilsMemberRepository repository;

    @Transactional
    public BeanUtilsMember patch(Long id, BeanUtilsMemberPatchRequest request) {
        BeanUtilsMember member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        // target(member) 의 setter 를 리플렉션으로 호출해서
        // source(request) 의 getter 값으로 덮어쓴다.
        // null 인 필드는 ignoreProperties 로 빼서 건너뛴다.
        String[] nullProps = BeanUtilsSupport.getNullPropertyNames(request);
        BeanUtils.copyProperties(request, member, nullProps);

        return member;
    }
}
