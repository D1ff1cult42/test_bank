package com.example.bankcards.controller.auth;

import com.example.bankcards.controller.ControllerTestSecurityConfig;
import com.example.bankcards.dto.request.AdminCreateUserRequest;
import com.example.bankcards.dto.request.UpdateRoleRequest;
import com.example.bankcards.dto.response.UserResponse;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.security.CookieUtils;
import com.example.bankcards.security.JwtService;
import com.example.bankcards.service.user.interfaces.UserService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(ControllerTestSecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UserService userService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CookieUtils cookieUtils;

    private UserResponse sampleUser(UUID id) {
        return UserResponse.builder()
                .id(id)
                .email("user@gmail.com")
                .username("user")
                .role(User.Role.USER)
                .active(true)
                .createdAt(Instant.now())
                .lastLogin(null)
                .build();
    }

    @Test
    void createUser_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.createUser(any())).thenReturn(sampleUser(id));

        var body = new AdminCreateUserRequest("user@gmail.com", "user", "password123", User.Role.USER);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("user@gmail.com"));
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        var body = new AdminCreateUserRequest("not-an-email", "user", "password123", User.Role.USER);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_shortPassword_returns400() throws Exception {
        var body = new AdminCreateUserRequest("user@gmail.com", "user", "short", User.Role.USER);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_duplicate_returns409() throws Exception {
        when(userService.createUser(any()))
                .thenThrow(new UserAlreadyExistsException("User with email user@gmail.com already exists"));

        var body = new AdminCreateUserRequest("user@gmail.com", "user", "password123", User.Role.USER);
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User Already Exists"));
    }

    @Test
    void getUsers_returnsPage() throws Exception {
        Page<UserResponse> page = new PageImpl<>(List.of(sampleUser(UUID.randomUUID())), Pageable.ofSize(20), 1);
        when(userService.getUsers(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenThrow(new UserNotFoundException("User not found: " + id));

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    void updateRole_returnsUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse updated = UserResponse.builder()
                .id(id).email("user@gmail.com").username("user")
                .role(User.Role.ADMIN).active(true).createdAt(Instant.now()).build();
        when(userService.updateRole(any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/users/{id}/role", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(User.Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void blockUser_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/users/{id}/block", id)).andExpect(status().isNoContent());
        verify(userService).blockUser(id);
    }

    @Test
    void activateUser_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/users/{id}/activate", id)).andExpect(status().isNoContent());
        verify(userService).activateUser(id);
    }

    @Test
    void deleteUser_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());
        verify(userService).deleteUser(id);
    }
}
