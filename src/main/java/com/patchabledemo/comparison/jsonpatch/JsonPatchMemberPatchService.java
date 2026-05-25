package com.patchabledemo.comparison.jsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class JsonPatchMemberPatchService {

    private final JsonPatchMemberRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JsonPatchMember patch(Long id, JsonPatch patch) throws JsonPatchException, IOException {
        JsonPatchMember member = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + id));

        // 1. 엔티티 -> JsonNode 트리 (getter 사용해 직렬화)
        JsonNode tree = objectMapper.valueToTree(member);

        // 2. 트리에 명령 배열을 적용
        JsonNode patched = patch.apply(tree);

        // 3. 트리 -> 새 엔티티 (setter 사용해 역직렬화). 도메인 메서드 우회.
        JsonPatchMember updated = objectMapper.treeToValue(patched, JsonPatchMember.class);

        // 4. 다시 저장. JPA dirty checking 의 자연스러운 흐름이 깨지므로 명시적 save.
        return repository.save(updated);
    }
}
