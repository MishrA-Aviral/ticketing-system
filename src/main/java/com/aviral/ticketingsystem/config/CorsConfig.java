package com.aviral.ticketingsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for the Tix-n-Trix ticketing API.
 *
 * <p>Registers permitted cross-origin rules for every API path through
 * Spring MVC's {@link CorsRegistry}. Using {@link WebMvcConfigurer} here
 * is the canonical approach: it integrates with Spring's handler-mapping
 * layer and avoids any conflict with {@code @CrossOrigin} annotations or
 * a Security filter chain (none is present in this project).
 *
 * <h3>Design decisions</h3>
 * <ul>
 *   <li>Origins are explicit — no wildcard {@code *} is used, which is
 *       required for any future request that carries credentials.</li>
 *   <li>The production frontend origin is supplied at runtime via the
 *       {@code FRONTEND_ORIGIN} environment variable so no production
 *       domain is hardcoded here.</li>
 *   <li>Only the HTTP methods actually consumed by the frontend are
 *       permitted: {@code GET}, {@code POST}, and the pre-flight
 *       {@code OPTIONS}.</li>
 *   <li>{@code Content-Type} is the only custom request header the
 *       frontend sends (for JSON POST bodies); exposing it explicitly
 *       keeps the allowed-header surface minimal.</li>
 *   <li>{@code allowCredentials} is intentionally left at its default
 *       ({@code false}): the API uses no cookies or HTTP auth.</li>
 * </ul>
 *
 * <h3>Environment variables</h3>
 * <pre>
 *   FRONTEND_ORIGIN   Production frontend base URL, e.g. https://tix-n-trix.example.com
 *                     If absent (local dev), only http://localhost:5173 is allowed.
 * </pre>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Production frontend origin injected from the {@code FRONTEND_ORIGIN}
     * environment variable. Defaults to an empty string so that the
     * {@code allowedOrigins} list degrades gracefully when the variable is
     * not set (local development).
     */
    @Value("${FRONTEND_ORIGIN:}")
    private String productionOrigin;

    /**
     * Registers CORS mappings for all API paths served by this application.
     *
     * <p>The mapping uses {@code /events/**} and {@code /bookings/**} to
     * cover every sub-resource — including {@code /bookings/{bookingId}/confirm}
     * — without opening CORS for unrelated paths such as Swagger UI or
     * actuator endpoints.
     *
     * @param registry the {@link CorsRegistry} provided by Spring MVC
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = buildAllowedOrigins();

        registry.addMapping("/events/**")
                .allowedOrigins(origins)
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders("Content-Type");

        registry.addMapping("/bookings/**")
                .allowedOrigins(origins)
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders("Content-Type");
    }

    /**
     * Builds the array of permitted origins.
     *
     * <ul>
     *   <li>{@code http://localhost:5173} is always included for local
     *       development with the Vite dev server.</li>
     *   <li>The production origin is appended only when {@code FRONTEND_ORIGIN}
     *       is non-blank, preventing an empty-string entry that some servlet
     *       containers treat as a wildcard.</li>
     * </ul>
     *
     * @return a non-empty array of allowed origin strings
     */
    String[] buildAllowedOrigins() {
        String localOrigin = "http://localhost:5173";

        if (productionOrigin != null && !productionOrigin.isBlank()) {
            return new String[]{localOrigin, productionOrigin.trim()};
        }

        return new String[]{localOrigin};
    }
}

