package com.example.bankcards.controller.card;

import com.example.bankcards.controller.ControllerTestSecurityConfig;
import com.example.bankcards.dto.request.CreateCardRequest;
import com.example.bankcards.dto.request.TransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.security.CookieUtils;
import com.example.bankcards.security.JwtService;
import com.example.bankcards.service.card.interfaces.CardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import(ControllerTestSecurityConfig.class)
class CardControllerTest {

    private static final String USER = "user@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private CardService cardService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CookieUtils cookieUtils;

    private CardResponse sampleCard(UUID id) {
        return CardResponse.builder()
                .id(id)
                .maskedNumber("**** **** **** 1234")
                .ownerUsername("user")
                .expiryDate(LocalDate.now().plusYears(4))
                .status(Card.Status.ACTIVE)
                .balance(new BigDecimal("100.00"))
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCard_asAdmin_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardService.createCard(any(CreateCardRequest.class))).thenReturn(sampleCard(id));

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCardRequest(UUID.randomUUID(), BigDecimal.ZERO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.maskedNumber").value("**** **** **** 1234"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCardRequest(UUID.randomUUID(), BigDecimal.ZERO))))
                .andExpect(status().isForbidden());
        verify(cardService, never()).createCard(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCard_invalidBody_returns400() throws Exception {
        // ownerId == null нарушает @NotNull
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCardRequest(null, BigDecimal.ZERO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllCards_asAdmin_returnsPage() throws Exception {
        Page<CardResponse> page = new PageImpl<>(List.of(sampleCard(UUID.randomUUID())), Pageable.ofSize(20), 1);
        when(cardService.getAllCards(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllCards_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/cards")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void getMyCards_passesAuthenticatedEmail() throws Exception {
        Page<CardResponse> page = new PageImpl<>(List.of(sampleCard(UUID.randomUUID())), Pageable.ofSize(20), 1);
        when(cardService.getOwnedCards(eq(USER), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/cards/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].maskedNumber").value("**** **** **** 1234"));

        verify(cardService).getOwnedCards(eq(USER), any(), any(), any());
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void getCard_returnsCard() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardService.getOwnedCard(USER, id)).thenReturn(sampleCard(id));

        mockMvc.perform(get("/api/cards/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void getCard_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardService.getOwnedCard(USER, id)).thenThrow(new CardNotFoundException("Card not found: " + id));

        mockMvc.perform(get("/api/cards/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Card Not Found"));
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void getBalance_returnsValue() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardService.getBalance(USER, id)).thenReturn(new BigDecimal("250.50"));

        mockMvc.perform(get("/api/cards/{id}/balance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(250.50));
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void requestBlock_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/cards/{id}/block-request", id))
                .andExpect(status().isNoContent());

        verify(cardService).requestBlock(USER, id);
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void transfer_returns204() throws Exception {
        TransferRequest req = new TransferRequest(new UUID(0, 1), new UUID(0, 2), new BigDecimal("30.00"));

        mockMvc.perform(post("/api/cards/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(cardService).transfer(eq(USER), any(TransferRequest.class));
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void transfer_invalidAmount_returns400() throws Exception {
        // amount = 0 нарушает @DecimalMin(0.01)
        TransferRequest req = new TransferRequest(new UUID(0, 1), new UUID(0, 2), BigDecimal.ZERO);

        mockMvc.perform(post("/api/cards/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verify(cardService, never()).transfer(any(), any());
    }

    @Test
    @WithMockUser(username = USER, roles = "USER")
    void transfer_insufficientFunds_returns400() throws Exception {
        TransferRequest req = new TransferRequest(new UUID(0, 1), new UUID(0, 2), new BigDecimal("50.00"));
        doThrow(new CardOperationException("Insufficient funds on the source card"))
                .when(cardService).transfer(eq(USER), any(TransferRequest.class));

        mockMvc.perform(post("/api/cards/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Card Operation Error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blockCard_asAdmin_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/cards/{id}/block", id)).andExpect(status().isNoContent());
        verify(cardService).blockCard(id);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateCard_asAdmin_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/cards/{id}/activate", id)).andExpect(status().isNoContent());
        verify(cardService).activateCard(id);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCard_asAdmin_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/cards/{id}", id)).andExpect(status().isNoContent());
        verify(cardService).deleteCard(id);
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteCard_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/cards/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(cardService, never()).deleteCard(any());
    }
}
