package com.login.backend.controller;

import com.login.backend.model.Role;
import com.login.backend.model.User;
import com.login.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            if (user.getRole() == null) {
                user.setRole(Role.USER);
            }

            User newUser = userService.register(user);

            return ResponseEntity.ok(Map.of(
                    "user", Map.of(
                            "username", newUser.getUsername(),
                            "role", "ROLE_" + newUser.getRole().name(),
                            "createdAt", newUser.getCreatedAt(),
                            "updatedAt", newUser.getUpdatedAt()
                    ),
                    "content", "User registered successfully!"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            String token = userService.login(username, password);
            User user = userService.findByUsername(username); // get full user info

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "user", Map.of(
                            "username", user.getUsername(),
                            "role", "ROLE_" + user.getRole().name(),
                            "createdAt", user.getCreatedAt(),
                            "updatedAt", user.getUpdatedAt()
                    ),
                    "content", "Hello " + username + "! This is your protected dashboard."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
}