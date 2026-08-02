package com.ratelimiter.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Component
public class RouteLimitConfigRegistry {
    private Map<String, RouteLimitConfig> configs = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("limits.json").getInputStream();
            configs = mapper.readValue(is, new TypeReference<Map<String, RouteLimitConfig>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to load limits.json", e);
        }
    }

    public RouteLimitConfig getConfig(String route) {
        return configs.get(route);
    }

    public Map<String, RouteLimitConfig> getAllConfigs() {
        return configs;
    }
}
