package com.patchabledemo.comparison.jackson;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/comparison/jackson/members")
@RequiredArgsConstructor
public class JacksonMemberPatchController {

    private final JacksonMemberPatchService service;

    // DTO 를 거치지 않고 raw JSON 문자열을 받아 엔티티에 직접 머지하는 모양.
    // 컨트롤러 레이어의 입력 검증/문서화가 약해지는 트레이드오프가 여기서 드러난다.
    @PatchMapping(value = "/{id}", consumes = "application/json")
    public JacksonMember patch(@PathVariable Long id, @RequestBody String jsonBody) throws IOException {
        return service.patch(id, jsonBody);
    }
}
