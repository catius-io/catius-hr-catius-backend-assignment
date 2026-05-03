package com.catius.order.service.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * {@link FeignInventoryClient}에만 적용되는 Feign 구성 (전역 ErrorDecoder를 오염시키지 않기 위해 분리).
 *
 * <p>{@code @Configuration} 을 붙이지 않은 이유는 Spring Cloud OpenFeign이 {@code configuration}
 * 옵션으로 명시 지정된 클래스만 해당 client scope으로 로드하기 때문. 전역 컴포넌트 스캔에 잡히면 모든
 * Feign client에 적용되어 의도와 어긋남.
 */
public class InventoryFeignConfig {

    @Bean
    public ErrorDecoder inventoryErrorDecoder(ObjectMapper objectMapper) {
        return new InventoryErrorDecoder(objectMapper);
    }
}
