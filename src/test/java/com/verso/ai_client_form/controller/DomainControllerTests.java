package com.verso.ai_client_form.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DomainControllerTests {

    private final DomainController controller = new DomainController();

    @Test
    void availabilityRejectsUrlsInsteadOfDomains() {
        ResponseEntity<Map<String, Object>> response = controller.availability("https://acme.it/login");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid", response.getBody().get("status"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("Inserisci solo il dominio"));
    }

    @Test
    void suggestReturnsExpectedItalianFriendlyVariants() {
        List<String> suggestions = controller.suggest("Acme Store");

        assertFalse(suggestions.isEmpty());
        assertEquals("acme-store.it", suggestions.get(0));
        assertTrue(suggestions.contains("acme-store.com"));
    }
}
