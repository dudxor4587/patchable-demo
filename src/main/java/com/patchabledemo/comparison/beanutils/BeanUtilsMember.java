package com.patchabledemo.comparison.beanutils;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// BeanUtils.copyProperties(src, target) 는 내부적으로
// target.setName(src.getName()) 같은 식으로 setter 를 호출한다.
// target 에 setter 가 없으면 그 필드는 "silent skip" — 에러 없이 그냥 건너뛴다.
// 그래서 BeanUtils 를 쓰려면 @Setter 가 필수.
@Entity
@Table(name = "comparison_beanutils_member")
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class BeanUtilsMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String nickname;
    private String phoneNumber;
    private String address;
    private String bio;
}
