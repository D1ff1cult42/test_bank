package com.example.bankcards.service.user;

import com.example.bankcards.dto.request.AdminCreateUserRequest;
import com.example.bankcards.dto.request.UpdateRoleRequest;
import com.example.bankcards.dto.response.UserResponse;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.user.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setUsername("user");
        user.setActive(true);
        user.setTokenVersion(2);
        user.setRole(User.Role.USER);
    }

    @Test
    void createUser_rejectsExistingEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                new AdminCreateUserRequest("user@example.com", "user", "password123", User.Role.ADMIN)))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_persistsWithGivenRole() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(
                new AdminCreateUserRequest("new@example.com", "newbie", "password123", User.Role.ADMIN));

        assertThat(response.role()).isEqualTo(User.Role.ADMIN);
        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("new@example.com")
                        && u.getPasswordHash().equals("hash")
                        && u.getRole() == User.Role.ADMIN
                        && u.isActive()));
    }

    @Test
    void blockUser_deactivatesAndBumpsTokenVersion() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.blockUser(user.getId());

        assertThat(user.isActive()).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(3);
    }

    @Test
    void activateUser_reactivatesWithoutTouchingTokenVersion() {
        user.setActive(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.activateUser(user.getId());

        assertThat(user.isActive()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void updateRole_changesRole() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = userService.updateRole(user.getId(), new UpdateRoleRequest(User.Role.ADMIN));

        assertThat(response.role()).isEqualTo(User.Role.ADMIN);
        assertThat(user.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void getUser_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUser_removesExistingUser() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deleteUser(user.getId());

        verify(userRepository).delete(user);
    }
}
