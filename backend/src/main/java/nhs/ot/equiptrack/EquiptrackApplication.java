package nhs.ot.equiptrack;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EquiptrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(EquiptrackApplication.class, args);
	}

	// Injected wherever "now" matters, so tests can control time
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
