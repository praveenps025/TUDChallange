package nhs.ot.equiptrack.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import nhs.ot.equiptrack.common.ApiExceptionHandler;

// All authentication wiring lives in the auth package. Controllers only see
// CurrentUser and role annotations, so hospital SSO can replace this
// configuration (and JwtUserConverter / AuthController) without touching them.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain api(HttpSecurity http, JwtUserConverter converter) throws Exception {
		http
				.csrf(csrf -> csrf.disable()) // bearer tokens only, no cookies
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(rs -> rs
						.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
						.authenticationEntryPoint(unauthorized())
						.accessDeniedHandler(forbidden()))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(unauthorized())
						.accessDeniedHandler(forbidden()));
		return http.build();
	}

	@Bean
	SecretKey jwtKey(JwtProperties props) {
		return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return NimbusJwtEncoder.withSecretKey(jwtKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER)); // checks expiry too
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(props.allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	// 401 and 403 are plain text like every other error, so the frontend can show them
	private static AuthenticationEntryPoint unauthorized() {
		return (request, response, e) -> {
			String message = e instanceof DisabledException ? e.getMessage()
					: request.getHeader("Authorization") == null ? "Please sign in"
					: "Your session has expired. Please sign in again.";
			response.setHeader("WWW-Authenticate", "Bearer");
			write(response, HttpStatus.UNAUTHORIZED, message);
		};
	}

	private static AccessDeniedHandler forbidden() {
		return (request, response, e) -> write(response, HttpStatus.FORBIDDEN, "You don't have permission to do that");
	}

	private static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(ApiExceptionHandler.TEXT_UTF8.toString());
		response.getWriter().write(message);
	}

}
