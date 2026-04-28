package com.minhnguyen.ecommerce;

import com.minhnguyen.ecommerce.dto.AuthResponse;
import com.minhnguyen.ecommerce.dto.LoginRequest;
import com.minhnguyen.ecommerce.dto.RegisterRequest;
import com.minhnguyen.ecommerce.entity.User;
import com.minhnguyen.ecommerce.repository.UserRepository;
import com.minhnguyen.ecommerce.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.ParseException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public AuthResponse register(RegisterRequest req) {
        if(userRepository.existsByEmail(req.getEmail()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already used");
        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);
        try {
            return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName());
        } catch (ParseException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        }
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if(!passwordEncoder.matches(user.getPasswordHash(), passwordEncoder.encode(req.getPassword())))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        try {
            return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName());
        } catch (ParseException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        }
    }
}
