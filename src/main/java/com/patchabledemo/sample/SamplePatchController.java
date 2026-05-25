package com.patchabledemo.sample;

import com.patchabledemo.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sample/members")
@RequiredArgsConstructor
public class SamplePatchController {

    private final SamplePatchService service;

    @PatchMapping("/{id}")
    public Member patch(@PathVariable Long id, @RequestBody SampleMemberProfilePatch request) {
        return service.patch(id, request);
    }
}
