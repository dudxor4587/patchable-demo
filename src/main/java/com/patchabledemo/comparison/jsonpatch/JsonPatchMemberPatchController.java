package com.patchabledemo.comparison.jsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/comparison/jsonpatch/members")
@RequiredArgsConstructor
public class JsonPatchMemberPatchController {

    private final JsonPatchMemberPatchService service;

    // 클라이언트가 보내는 본문은 명령 배열:
    //   [ {"op": "replace", "path": "/bio", "value": "hello"} ]
    //
    // JsonNode 로 받아 JsonPatch.fromJson() 으로 변환하는 게 가장 안정적이다.
    @PatchMapping(value = "/{id}", consumes = "application/json-patch+json")
    public JsonPatchMember patch(@PathVariable Long id, @RequestBody JsonNode patchNode)
            throws JsonPatchException, IOException {
        JsonPatch patch = JsonPatch.fromJson(patchNode);
        return service.patch(id, patch);
    }
}
