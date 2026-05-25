package com.patchabledemo.comparison.jackson;

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

// readerForUpdating(member) 는 기존 인스턴스에 setter 로 필드를 덮어쓴다.
// setter 가 없으면 패치가 silent 하게 무동작이 되므로 @Setter 필수.
@Entity
@Table(name = "comparison_jackson_member")
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class JacksonMember {

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
