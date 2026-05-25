package com.patchabledemo.comparison.beanutils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 일반 POJO 로 작성한다.
// BeanUtils.copyProperties 는 JavaBeans 규약(getName/setName) 으로 동작하기 때문에
// record (name() accessor) 와는 잘 맞지 않는다. 이게 BeanUtils 의 한계 중 하나.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BeanUtilsMemberPatchRequest {

    private String name;
    private String email;
    private String nickname;
    private String phoneNumber;
    private String address;
    private String bio;
}
