package ru.avitodeals.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:api-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ApiControllerTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void searchCrudReturnsDocumentedHttpStatusesAndValidatesPagination() throws Exception {
		String request = """
				{"name":"Pixel","query":"Google Pixel","filters":{"priceMin":10000,"location":"Москва"},
				 "intervalMinutes":60,"minDealScore":40,"active":true}
				""";
		String location = mvc.perform(post("/api/searches")
						.contentType("application/json").content(request))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Pixel"))
				.andReturn().getResponse().getHeader("Location");

		String id = location.substring(location.lastIndexOf('/') + 1);
		mvc.perform(get("/api/searches").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(id))
				.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(put("/api/searches/{id}", id).contentType("application/json")
						.content(request.replace("Pixel", "Updated")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Updated"));
		mvc.perform(delete("/api/searches/{id}", id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/searches/{id}", id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/listings").param("size", "101")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/searches").contentType("application/json")
						.content(request.replace("\"priceMin\":10000", "\"priceMin\":90000")
								.replace("\"location\":\"Москва\"", "\"priceMax\":1000")))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/deals").param("size", "101")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/deals")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/listings")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/deals/{id}", java.util.UUID.randomUUID())).andExpect(status().isNotFound());
		mvc.perform(get("/api/stats")).andExpect(status().isOk())
				.andExpect(jsonPath("$.searches").value(0));
		mvc.perform(post("/api/profitability/calculate")
						.contentType("application/json")
						.content("""
								{"purchasePrice":800,"resalePrice":1200,"logisticsCost":25,"repairCost":50,
								 "sellingFees":60,"otherCosts":10,"riskReserve":20,"minimumRequiredProfit":35,
								 "pessimisticResalePrice":1000,"optimisticResalePrice":1500,"currency":"RUB"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.netProfit").value(235))
				.andExpect(jsonPath("$.roiPercent").value(25.97))
				.andExpect(jsonPath("$.scenarios[0].name").value("PESSIMISTIC"));
		mvc.perform(post("/api/profitability/calculate")
						.contentType("application/json")
						.content("""
								{"purchasePrice":800,"resalePrice":1200,"logisticsCost":-1,"repairCost":0,
								 "sellingFees":0,"otherCosts":0,"riskReserve":0,"minimumRequiredProfit":0,
								 "pessimisticResalePrice":1000,"optimisticResalePrice":1500,"currency":"RUB"}
								"""))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/profitability/calculate")
						.contentType("application/json")
						.content("""
								{"purchasePrice":800,"resalePrice":1200,"repairCost":0,"sellingFees":0,
								 "otherCosts":0,"riskReserve":0,"minimumRequiredProfit":0,
								 "pessimisticResalePrice":1000,"optimisticResalePrice":1500,"currency":"RUB"}
								"""))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/searches']").exists());
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
