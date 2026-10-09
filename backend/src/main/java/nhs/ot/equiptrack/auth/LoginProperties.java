package nhs.ot.equiptrack.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.login")
public record LoginProperties(int maxFailedAttempts, Duration lockDuration) {
}
