package nhs.ot.equiptrack;

import org.springframework.boot.SpringApplication;

public class TestEquiptrackApplication {

	public static void main(String[] args) {
		SpringApplication.from(EquiptrackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
