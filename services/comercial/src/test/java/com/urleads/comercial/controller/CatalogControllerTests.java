package com.urleads.comercial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.comercial.dto.CatalogItemRequest;
import com.urleads.comercial.repository.CatalogItemRepository;
import com.urleads.comercial.security.JwtTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String user42Token;
    private String user99Token;

    @BeforeEach
    void setUp() {
        catalogItemRepository.deleteAll();
        user42Token = JwtTestUtils.generateValidToken("42", "comercial.access");
        user99Token = JwtTestUtils.generateValidToken("99", "comercial.access");
    }

    @Test
    @DisplayName("POST /api/v1/catalog creates item successfully with 201 Created")
    void createCatalogItem_success() throws Exception {
        CatalogItemRequest request = new CatalogItemRequest(
            "SRV-01",
            "Consultoria Inicial",
            "Servicio de consultoria para empresas",
            "USD",
            new BigDecimal("150.555"),
            true
        );

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.code").value("SRV-01"))
            .andExpect(jsonPath("$.name").value("Consultoria Inicial"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.unitPrice").value(150.56))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/catalog rejects non-positive price with 400 INVALID_PRICE")
    void createCatalogItem_invalidPrice_returns400() throws Exception {
        CatalogItemRequest request = new CatalogItemRequest(
            "SRV-02",
            "Consultoria Gratis",
            "Invalido",
            "USD",
            new BigDecimal("0.00"),
            true
        );

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_PRICE"))
            .andExpect(jsonPath("$.message").value("El precio unitario debe ser un valor decimal positivo."))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("unitPrice"));
    }

    @Test
    @DisplayName("POST /api/v1/catalog rejects invalid currency with 400 INVALID_CURRENCY")
    void createCatalogItem_invalidCurrency_returns400() throws Exception {
        CatalogItemRequest request = new CatalogItemRequest(
            "SRV-03",
            "Consultoria Euros",
            "Moneda no soportada",
            "EUR",
            new BigDecimal("100.00"),
            true
        );

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    @DisplayName("POST /api/v1/catalog rejects duplicate code for same owner with 409 DUPLICATE_CODE")
    void createCatalogItem_duplicateCode_returns409() throws Exception {
        CatalogItemRequest request1 = new CatalogItemRequest(
            "PROD-01",
            "Producto Alpha",
            "Desc",
            "PEN",
            new BigDecimal("50.00"),
            true
        );

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        CatalogItemRequest request2 = new CatalogItemRequest(
            "PROD-01",
            "Producto Alpha Duplicado",
            "Desc",
            "PEN",
            new BigDecimal("75.00"),
            true
        );

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    @DisplayName("Same code can exist for different owners without conflict")
    void createCatalogItem_sameCodeDifferentOwners_success() throws Exception {
        CatalogItemRequest request = new CatalogItemRequest(
            "SHARED-CODE",
            "Producto",
            "Desc",
            "USD",
            new BigDecimal("20.00"),
            true
        );

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /api/v1/catalog isolates items by owner and supports pagination")
    void getCatalog_userIsolation_success() throws Exception {
        // Create 2 items for user 42
        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest("C1", "Item 1", "", "USD", new BigDecimal("10.00"), true))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest("C2", "Item 2", "", "PEN", new BigDecimal("20.00"), false))))
            .andExpect(status().isCreated());

        // Create 1 item for user 99
        mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest("C99", "Secret 99", "", "USD", new BigDecimal("99.00"), true))))
            .andExpect(status().isCreated());

        // User 42 lists: should see only 2 items
        mockMvc.perform(get("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].code").exists())
            .andExpect(jsonPath("$.content[1].code").exists());

        // User 42 filters by currency=USD: should see 1 item
        mockMvc.perform(get("/api/v1/catalog?currency=USD")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].code").value("C1"));

        // User 99 lists: should see only 1 item
        mockMvc.perform(get("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].code").value("C99"));
    }

    @Test
    @DisplayName("GET /api/v1/catalog/{id} returns 404 when item belongs to another owner")
    void getById_otherOwnerItem_returns404() throws Exception {
        // Create item for user 99
        String responseStr = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest("SECRET-99", "Confidencial", "", "USD", new BigDecimal("500.00"), true))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long id99 = objectMapper.readTree(responseStr).get("id").asLong();

        // User 42 requests user 99's item: must return 404 without leaking existence
        mockMvc.perform(get("/api/v1/catalog/" + id99)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/v1/catalog/{id} updates own item and rejects other owner's item")
    void updateCatalogItem_isolation_and_success() throws Exception {
        String responseStr = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest("UPD-01", "Old Name", "Old Desc", "USD", new BigDecimal("100.00"), true))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseStr).get("id").asLong();

        // User 99 tries to update user 42's item -> 404
        CatalogItemRequest updateRequest = new CatalogItemRequest("UPD-01", "Hacked Name", "Desc", "USD", new BigDecimal("1.00"), false);
        mockMvc.perform(put("/api/v1/catalog/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isNotFound());

        // User 42 updates own item -> 200 OK
        CatalogItemRequest validUpdate = new CatalogItemRequest("UPD-01", "New Name", "New Desc", "PEN", new BigDecimal("350.00"), false);
        mockMvc.perform(put("/api/v1/catalog/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.currency").value("PEN"))
            .andExpect(jsonPath("$.unitPrice").value(350.00))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/catalog/{id} deletes own item and returns 404 for other owner's item")
    void deleteCatalogItem_isolation_and_success() throws Exception {
        String responseStr = mockMvc.perform(post("/api/v1/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CatalogItemRequest("DEL-01", "To Delete", "", "USD", new BigDecimal("50.00"), true))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseStr).get("id").asLong();

        // User 99 tries to delete user 42's item -> 404
        mockMvc.perform(delete("/api/v1/catalog/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user99Token))
            .andExpect(status().isNotFound());

        // User 42 deletes own item -> 204 No Content
        mockMvc.perform(delete("/api/v1/catalog/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNoContent());

        // Verifying it is gone
        mockMvc.perform(get("/api/v1/catalog/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user42Token))
            .andExpect(status().isNotFound());
    }
}
