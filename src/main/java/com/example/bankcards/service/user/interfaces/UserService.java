package com.example.bankcards.service.user.interfaces;

import com.example.bankcards.dto.request.AdminCreateUserRequest;
import com.example.bankcards.dto.request.UpdateRoleRequest;
import com.example.bankcards.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserResponse createUser(AdminCreateUserRequest request);

    Page<UserResponse> getUsers(String search, Pageable pageable);

    UserResponse getUser(UUID id);

    UserResponse updateRole(UUID id, UpdateRoleRequest request);

    void blockUser(UUID id);

    void activateUser(UUID id);

    void deleteUser(UUID id);
}
