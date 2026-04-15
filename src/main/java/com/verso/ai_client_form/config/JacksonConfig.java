package com.verso.ai_client_form.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // Ensure an ObjectMapper bean exists even if auto-config is not providing one.
        // Register Java time, etc, if available on the classpath.
        return new ObjectMapper().findAndRegisterModules();
    }
}
