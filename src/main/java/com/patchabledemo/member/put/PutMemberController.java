package com.patchabledemo.member.put;

import com.patchabledemo.member.Member;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class PutMemberController {

    private final PutMemberService service;

    public PutMemberController(PutMemberService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public Member replace(@PathVariable Long id, @RequestBody MemberPutRequest request) {
        return service.replace(id, request);
    }
}
