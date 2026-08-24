package com.suptech.postventa.infrastructure.config;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AplicacionConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean
    CountedAspect countedAspect(MeterRegistry meterRegistry) {
        return new CountedAspect(meterRegistry);
    }
}
