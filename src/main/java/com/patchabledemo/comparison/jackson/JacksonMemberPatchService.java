package com.patchabledemo.comparison.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class JacksonMemberPatchService {

    private final JacksonMemberRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JacksonMember patch(Long id, String jsonBody) throws IOException {
        JacksonMember member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        // JSON 에 명시된 필드만 기존 인스턴스에 덮어쓴다.
        // 내부 동작: Jackson 이 JSON 토큰을 읽으면서 매칭되는 setter 를 호출.
        // JSON 에 없는 필드는 setter 가 호출되지 않으므로 기존 값이 유지된다.
        objectMapper.readerForUpdating(member).readValue(jsonBody);

        return member;
    }
}
