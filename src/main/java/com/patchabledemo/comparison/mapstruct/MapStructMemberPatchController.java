package com.patchabledemo.comparison.mapstruct;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparison/mapstruct/members")
@RequiredArgsConstructor
public class MapStructMemberPatchController {

    private final MapStructMemberPatchService service;

    @PatchMapping("/{id}")
    public MapStructMember patch(
            @PathVariable Long id,
            @RequestBody MapStructMemberPatchRequest request
    ) {
        return service.patch(id, request);
    }
}
