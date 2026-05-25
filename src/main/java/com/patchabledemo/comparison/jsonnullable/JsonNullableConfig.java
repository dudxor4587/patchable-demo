package com.patchabledemo.comparison.jsonnullable;

import com.fasterxml.jackson.databind.Module;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// JsonNullableModule 을 Jackson 에 등록해야
// "필드 미포함" 과 "명시적 null" 을 구분해서 역직렬화할 수 있다.
@Configuration
public class JsonNullableConfig {

    @Bean
    public Module jsonNullableModule() {
        return new JsonNullableModule();
    }
}
