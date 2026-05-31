package com.bank.transaction.config;

import com.bank.transaction.feign.FeignErrorDecoder;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            5, TimeUnit.SECONDS,   // connect timeout
            10, TimeUnit.SECONDS,  // read timeout
            true                   // follow redirects
        );
    }

    @Bean
    public Retryer feignRetryer() {
        // Retry 3 ครั้ง, เริ่มต้นที่ 100ms, สูงสุด 1 วินาที
        return new Retryer.Default(100, 1000, 3);
    }

    @Bean
    public FeignErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}
