package com.patchabledemo.comparison.mapstruct;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// componentModel = "spring" -> 생성된 구현체가 @Component 로 붙어 빈으로 등록.
//
// 컴파일 후 build/generated/sources/annotationProcessor/.../MapStructMemberPatchMapperImpl.java
// 에 가보면 대충 이런 코드가 자동 생성된다:
//
//   @Override
//   public void patch(MapStructMemberPatchRequest request, MapStructMember member) {
//       if (request.name() != null)        member.setName(request.name());
//       if (request.email() != null)       member.setEmail(request.email());
//       if (request.nickname() != null)    member.setNickname(request.nickname());
//       ...
//   }
//
// 이 자동 생성 코드가 setter 를 직접 호출하기 때문에 @MappingTarget 에 @Setter 가 필수.
@Mapper(componentModel = "spring")
public interface MapStructMemberPatchMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void patch(MapStructMemberPatchRequest request, @MappingTarget MapStructMember member);
}
