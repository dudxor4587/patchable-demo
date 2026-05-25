package com.patchabledemo.comparison.mapstruct;

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

// MapStruct 는 컴파일 타임에 매퍼 구현체를 소스 코드로 생성한다.
// 생성된 코드가 member.setName(req.name()) 식으로 setter 를 호출하기 때문에
// @MappingTarget 으로 지정된 클래스에는 @Setter 가 반드시 필요하다.
// 없으면 BeanUtils 와 달리 silent skip 이 아니라 컴파일 에러가 난다.
@Entity
@Table(name = "comparison_mapstruct_member")
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class MapStructMember {

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
