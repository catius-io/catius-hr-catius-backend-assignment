package com.catius.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * InventoryClient 전용 Feign 설정. @FeignClient 의 configuration 으로 지정되어 이 client 에만 적용.
 *
 * 주의: @Configuration 을 *붙이지 않는다*. Spring 이 component-scan 으로 이 빈을 모든 컨텍스트에
 * 등록하면 Feign 의 default ErrorDecoder 가 전역적으로 교체되어 다른 client 의 동작을 바꿀 수 있음.
 * @FeignClient(configuration = ...) 로 지정하면 Spring 이 이 클래스 안의 @Bean 을
 * 해당 client 에만 등록한다.
 */
public class InventoryClientConfig {

    @Bean
    public ErrorDecoder inventoryClientErrorDecoder(ObjectMapper objectMapper) {
        return new InventoryClientErrorDecoder(objectMapper);
    }
}
