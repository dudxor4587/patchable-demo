package com.patchabledemo.member.patch;

import com.patchabledemo.member.Member;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/members")
public class PatchMemberController {

    private final PatchMemberService service;

    public PatchMemberController(PatchMemberService service) {
        this.service = service;
    }

    @PatchMapping("/{id}")
    public Member patch(@PathVariable Long id, @RequestBody MemberPatchRequest request) {
        return service.patch(id, request);
    }
}
