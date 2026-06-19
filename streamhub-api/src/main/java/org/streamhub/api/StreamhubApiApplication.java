package org.streamhub.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StreamHub Admin internal API.
 *
 * <p>Portfolio clone of the a production service admin API. Demonstrates the same production stack:
 * Spring Boot 3.4 / Java 21, stateless JWT security, JPA + MyBatis hybrid persistence,
 * Redis caching, and S3-compatible object storage (MinIO).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("org.streamhub.api.**.mapper")
@EnableScheduling
@EnableAsync
public class StreamhubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamhubApiApplication.class, args);
    }
}
