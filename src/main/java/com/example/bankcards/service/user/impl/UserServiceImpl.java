package com.example.bankcards.service.user.impl;

import com.example.bankcards.dto.request.AdminCreateUserRequest;
import com.example.bankcards.dto.request.UpdateRoleRequest;
import com.example.bankcards.dto.response.UserResponse;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.user.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("User with email " + request.email() + " already exists");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(true);

        user = userRepository.save(user);
        log.info("Admin created user {} with role {}", user.getId(), user.getRole());
        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String search, Pageable pageable) {
        String q = (search == null || search.isBlank()) ? null : search.trim();
        return userRepository.search(q, pageable).map(UserResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return UserResponse.from(getEntity(id));
    }

    @Override
    @Transactional
    public UserResponse updateRole(UUID id, UpdateRoleRequest request) {
        User user = getEntity(id);
        user.setRole(request.role());
        log.info("User {} role changed to {}", id, request.role());
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void blockUser(UUID id) {
        User user = getEntity(id);
        user.setActive(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        log.info("User {} blocked", id);
    }

    @Override
    @Transactional
    public void activateUser(UUID id) {
        User user = getEntity(id);
        user.setActive(true);
        log.info("User {} activated", id);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = getEntity(id);
        userRepository.delete(user);
        log.info("User {} deleted", id);
    }

    private User getEntity(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }
}
