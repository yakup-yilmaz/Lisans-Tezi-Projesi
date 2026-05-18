package com.tezprojesi.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebCorsConfig {

	@Bean
	public CorsFilter corsFilter() {
		CorsConfiguration config = new CorsConfiguration();
		
		// Restrict allowed origins for security
		config.addAllowedOrigin("http://localhost:3000");
		config.addAllowedOrigin("http://localhost:5173");
		config.addAllowedOrigin("http://localhost:5174");
		config.addAllowedOrigin("http://127.0.0.1:3000");
		config.addAllowedOrigin("http://127.0.0.1:5173");
		config.addAllowedOrigin("http://127.0.0.1:5174");
		// In production, add your domain:
		// config.addAllowedOrigin("https://yourdomain.com");
		
		config.addAllowedHeader("Content-Type");
		config.addAllowedHeader("Authorization");
		config.addAllowedHeader("X-Requested-With");
		
		config.addAllowedMethod("GET");
		config.addAllowedMethod("POST");
		config.addAllowedMethod("PUT");
		config.addAllowedMethod("DELETE");
		config.addAllowedMethod("OPTIONS");
		
		// CRITICAL: Do NOT set allowCredentials to true when using wildcard origins
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return new CorsFilter(source);
	}
}

