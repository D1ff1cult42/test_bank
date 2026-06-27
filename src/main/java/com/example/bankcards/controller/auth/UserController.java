package com.example.bankcards.controller.auth;

import com.example.bankcards.dto.request.AdminCreateUserRequest;
import com.example.bankcards.dto.request.UpdateRoleRequest;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.dto.response.UserResponse;
import com.example.bankcards.service.user.interfaces.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Пользователи", description = "Администрирование пользователей (только ADMIN)")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Создать пользователя с заданной ролью")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @Operation(summary = "Список пользователей с поиском и пагинацией")
    @GetMapping
    public PageResponse<UserResponse> getUsers(@RequestParam(required = false) String search,
                                               @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(userService.getUsers(search, pageable));
    }

    @Operation(summary = "Получить пользователя по id")
    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    @Operation(summary = "Изменить роль пользователя")
    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return userService.updateRole(id, request);
    }

    @Operation(summary = "Заблокировать пользователя")
    @PostMapping("/{id}/block")
    public ResponseEntity<Void> blockUser(@PathVariable UUID id) {
        userService.blockUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Активировать пользователя")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable UUID id) {
        userService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Удалить пользователя")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
