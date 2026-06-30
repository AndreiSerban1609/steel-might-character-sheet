package com.steelmight.charactersheet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Comma-separated allowed origins. Override per environment with CORS_ORIGINS
    // (e.g. add your Cloudflare Tunnel / Pages origin) — never use "*", the API is unauthenticated.
    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.origins:https://andreiserban1609.github.io,http://localhost:5173}") String origins) {
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
