package com.kmicro.shared.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One UTC clock for the whole app. Take {@link Clock} as a bean instead of calling {@code Instant.now()}. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
