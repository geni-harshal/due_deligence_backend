package com.entitycheck.controller;

import com.entitycheck.config.JwtUtil;
import com.entitycheck.dto.LoginRequest;
import com.entitycheck.dto.LoginResponse;
import com.entitycheck.dto.UserDto;
import com.entitycheck.model.User;
import com.entitycheck.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Authenticate credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Generate JWT token
        String token = jwtUtil.generateToken(request.email());

        // Load full user details with company eagerly
        User user = userRepository.findByEmailWithCompany(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new LoginResponse(token, toDto(user)));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = auth.getName();
        User user = userRepository.findByEmailWithCompany(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // With JWT, logout is handled client-side by removing the token.
        // This endpoint exists for API consistency.
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    private UserDto toDto(User user) {
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole().name().toLowerCase(),
            user.getClientCompany() != null ? user.getClientCompany().getId() : null,
            user.getClientCompany() != null ? user.getClientCompany().getName() : null,
            user.isActive()
        );
    }
}
