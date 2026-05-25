package com.patchabledemo.comparison.jsonnullable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 도메인 메서드를 통해서만 상태가 바뀌는 원래 Member 와 동일한 구조.
// JsonNullable 해법은 엔티티는 그대로 두고 DTO 만 바꾼다.
@Entity
@Table(name = "comparison_jsonnullable_member")
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JsonNullableMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String nickname;
    private String phoneNumber;
    private String address;
    private String bio;

    public void updateMember(String name,
                             String email,
                             String nickname,
                             String phoneNumber,
                             String address,
                             String bio) {
        this.name = name;
        this.email = email;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.bio = bio;
    }
}
