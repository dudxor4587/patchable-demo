package com.patchabledemo.comparison.beanutils;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparison/beanutils/members")
@RequiredArgsConstructor
public class BeanUtilsMemberPatchController {

    private final BeanUtilsMemberPatchService service;

    @PatchMapping("/{id}")
    public BeanUtilsMember patch(
            @PathVariable Long id,
            @RequestBody BeanUtilsMemberPatchRequest request
    ) {
        return service.patch(id, request);
    }
}
