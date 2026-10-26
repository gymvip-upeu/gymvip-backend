package com.gymvip.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MembershipConfig {
    @Bean public Clock membershipClock() { return Clock.systemUTC(); }
}
