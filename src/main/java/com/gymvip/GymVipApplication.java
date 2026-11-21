package com.gymvip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@org.springframework.scheduling.annotation.EnableScheduling
public class GymVipApplication {
    public static void main(String[] args) {
        SpringApplication.run(GymVipApplication.class, args);
    }
}
