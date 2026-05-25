package com.patchabledemo.comparison.jsonnullable;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparison/jsonnullable/members")
@RequiredArgsConstructor
public class JsonNullableMemberPatchController {

    private final JsonNullableMemberPatchService service;

    @PatchMapping("/{id}")
    public JsonNullableMember patch(
            @PathVariable Long id,
            @RequestBody JsonNullableMemberPatchRequest request
    ) {
        return service.patch(id, request);
    }
}
