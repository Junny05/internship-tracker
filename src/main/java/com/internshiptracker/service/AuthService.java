package com.internshiptracker.service;

import com.internshiptracker.dto.AuthResponse;
import com.internshiptracker.dto.LoginRequest;
import com.internshiptracker.dto.RegisterRequest;
import com.internshiptracker.entity.User;
import com.internshiptracker.exception.EmailAlreadyInUseException;
import com.internshiptracker.exception.InvalidCredentialsException;
import com.internshiptracker.repository.UserRepository;
import com.internshiptracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.builder().token(token).tokenType("Bearer").build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.builder().token(token).tokenType("Bearer").build();
    }
}
