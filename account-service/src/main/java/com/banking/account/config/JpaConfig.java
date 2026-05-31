package com.banking.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA Configuration
 * — เปิดใช้ JPA Auditing สำหรับ @CreationTimestamp, @UpdateTimestamp
 * — เปิดใช้ Transaction Management
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.banking.account.repository")
@EnableTransactionManagement
public class JpaConfig {
    // Spring Boot auto-configures DataSource, EntityManagerFactory, TransactionManager
    // จาก application.yml ไม่ต้อง declare bean เพิ่มเติมที่นี่
}
