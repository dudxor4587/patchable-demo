package com.patchabledemo.comparison.jsonpatch;

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

// 동작 흐름:
//   entity -> objectMapper.valueToTree(entity)   // 트리로 변환 (getter 사용)
//   patch.apply(tree)                            // 트리에 명령 적용
//   objectMapper.treeToValue(tree, Entity.class) // 트리에서 다시 엔티티로 (setter 사용)
//
// 마지막 단계에서 setter 가 필요하다. 도메인 메서드(updateMember 류) 는 우회된다.
@Entity
@Table(name = "comparison_jsonpatch_member")
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class JsonPatchMember {

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
