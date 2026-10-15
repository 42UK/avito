package ru.avitodeals.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
		title = "DealRadar API",
		version = "v1",
		description = "Local DealRadar API for searches, listings, profitability calculations, saved deals, and statistics."))
public class OpenApiConfiguration {
}
