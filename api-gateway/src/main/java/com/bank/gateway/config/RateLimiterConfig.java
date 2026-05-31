package com.banking.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimiterConfig {

    /**
     * Rate limit แยกตาม IP Address
     * ถ้า X-Forwarded-For มี (อยู่หลัง Nginx/LB) ใช้ IP แรก
     * ไม่งั้นใช้ remote address โดยตรง
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String xForwardedFor = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");

            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                // X-Forwarded-For อาจมีหลาย IP เช่น "1.2.3.4, 5.6.7.8" เอาแรกสุด
                String clientIp = xForwardedFor.split(",")[0].trim();
                return Mono.just(clientIp);
            }

            return Mono.just(
                Objects.requireNonNull(
                    exchange.getRequest().getRemoteAddress()
                ).getAddress().getHostAddress()
            );
        };
    }

    /**
     * Rate limit แยกตาม User (ต้องมี JWT แล้ว)
     * ใช้ร่วมกับ JwtAuthenticationFilter ที่ inject X-User-Id
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId != null ? userId : "anonymous");
        };
    }
}
