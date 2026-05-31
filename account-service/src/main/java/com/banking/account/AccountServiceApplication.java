package com.banking.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Account Service — Banking Core Service
 *
 * Port: 8082 (configurable via application.yml)
 * DB:   account_db (PostgreSQL)
 *
 * ─── Startup Checklist ───────────────────────────────────────────────────────
 *  ✅  PostgreSQL ต้องรันอยู่และ database 'account_db' ต้องมีอยู่
 *  ✅  ตั้งค่า DB_USERNAME / DB_PASSWORD environment variable
 *      หรือแก้ใน application.yml
 *
 * ─── Quick Start ─────────────────────────────────────────────────────────────
 *  mvn spring-boot:run
 *  หรือ
 *  java -jar target/account-service-1.0.0.jar
 */
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
