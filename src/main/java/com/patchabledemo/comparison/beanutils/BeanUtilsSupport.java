package com.patchabledemo.comparison.beanutils;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.stream.Stream;

public final class BeanUtilsSupport {

    // source 객체에서 값이 null 인 프로퍼티의 이름들을 모은다.
    // BeanUtils.copyProperties 의 ignoreProperties 인자로 넘기면
    // "null 인 필드는 복사 대상에서 제외" 효과를 만들 수 있다.
    public static String[] getNullPropertyNames(Object source) {
        BeanWrapper wrapped = new BeanWrapperImpl(source);
        return Stream.of(wrapped.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> wrapped.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }

    private BeanUtilsSupport() {}
}
