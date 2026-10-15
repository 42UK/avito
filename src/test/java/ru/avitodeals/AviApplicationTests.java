package ru.avitodeals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.avitodeals.ingestion.application.ListingSource;
import ru.avitodeals.ingestion.infrastructure.avito.AvitoListingSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:avito;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=false"
})
class AviApplicationTests {

	@Autowired
	private ListingSource listingSource;

	@Test
	void contextLoads() {
		assertInstanceOf(AvitoListingSource.class, listingSource);
	}

}
