package com.login.backend.controller;

import com.login.backend.model.User;
import com.login.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final UserService userService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);

        Map<String, Object> response = Map.of(
                "user", Map.of(
                        "username", user.getUsername(),
                        "role", "ROLE_" + user.getRole().name(),
                        "createdAt", user.getCreatedAt(),
                        "updatedAt", user.getUpdatedAt()
                ),
                "content", "Hello " + user.getUsername() + "! This is your protected dashboard."
        );

        return ResponseEntity.ok(response);
    }
}