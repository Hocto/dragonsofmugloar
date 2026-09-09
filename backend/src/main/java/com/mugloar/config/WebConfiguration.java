package com.mugloar.config;

import com.mugloar.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS exists here for exactly one reason: {@code npm run dev} serves the UI from port 5173 while
 * the API is on 8080. In Docker nginx proxies /api to the backend, so the browser makes same-origin
 * requests and this configuration does nothing.
 *
 * <p>The origins are a config value rather than a wildcard so a deployed instance is not quietly
 * callable from anywhere.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final WebProperties properties;

    public WebConfiguration(WebProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.allowedOrigins() == null || properties.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
