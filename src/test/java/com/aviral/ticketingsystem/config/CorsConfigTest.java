package com.aviral.ticketingsystem.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorsConfig}.
 * Verifies allowed origins, HTTP methods, request headers, and absence of wildcard origins or credentials.
 */
class CorsConfigTest {

    private static class TestableCorsRegistry extends CorsRegistry {
        public Map<String, CorsConfiguration> exposedCorsConfigurations() {
            return getCorsConfigurations();
        }
    }

    @Test
    @DisplayName("Default configuration allows localhost:5173 without wildcard or credentials")
    void defaultConfiguration_AllowsLocalhost5173Only() {
        CorsConfig corsConfig = new CorsConfig();
        TestableCorsRegistry registry = new TestableCorsRegistry();

        corsConfig.addCorsMappings(registry);
        Map<String, CorsConfiguration> configs = registry.exposedCorsConfigurations();

        assertThat(configs).containsKeys("/events/**", "/bookings/**");

        CorsConfiguration eventsConfig = configs.get("/events/**");
        assertThat(eventsConfig.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(eventsConfig.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "OPTIONS");
        assertThat(eventsConfig.getAllowedHeaders()).containsExactly("Content-Type");
        assertThat(eventsConfig.getAllowCredentials()).isNull();

        CorsConfiguration bookingsConfig = configs.get("/bookings/**");
        assertThat(bookingsConfig.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(bookingsConfig.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "OPTIONS");
        assertThat(bookingsConfig.getAllowedHeaders()).containsExactly("Content-Type");
        assertThat(bookingsConfig.getAllowCredentials()).isNull();
    }

    @Test
    @DisplayName("When FRONTEND_ORIGIN is set, includes both local and production origins without wildcards")
    void withProductionOrigin_AllowsBothOrigins() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "productionOrigin", "https://tix-n-trix.example.com");

        TestableCorsRegistry registry = new TestableCorsRegistry();
        corsConfig.addCorsMappings(registry);
        Map<String, CorsConfiguration> configs = registry.exposedCorsConfigurations();

        CorsConfiguration eventsConfig = configs.get("/events/**");
        assertThat(eventsConfig.getAllowedOrigins()).containsExactly(
                "http://localhost:5173",
                "https://tix-n-trix.example.com"
        );
        assertThat(eventsConfig.getAllowedOrigins()).doesNotContain("*");

        CorsConfiguration bookingsConfig = configs.get("/bookings/**");
        assertThat(bookingsConfig.getAllowedOrigins()).containsExactly(
                "http://localhost:5173",
                "https://tix-n-trix.example.com"
        );
        assertThat(bookingsConfig.getAllowedOrigins()).doesNotContain("*");
    }

    @Test
    @DisplayName("Blank or whitespace FRONTEND_ORIGIN is ignored and only localhost:5173 is allowed")
    void blankProductionOrigin_DegradesGracefully() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "productionOrigin", "   ");

        String[] origins = corsConfig.buildAllowedOrigins();
        assertThat(origins).containsExactly("http://localhost:5173");
    }
}
