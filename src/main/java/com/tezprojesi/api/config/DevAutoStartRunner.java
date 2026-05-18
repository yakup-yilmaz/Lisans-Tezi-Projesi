package com.tezprojesi.api.config;

import com.tezprojesi.simulation.SimulationEngine;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile({"default","dev"})
public class DevAutoStartRunner {

	@Bean
	@Order(2)
	public CommandLineRunner autoStartSimulation(SimulationEngine simulationEngine, SimulationProperties props) {
		return args -> {
			if (props.isAutostart() && !simulationEngine.isRunning()) {
				simulationEngine.startAsync();
			}
		};
	}
}

