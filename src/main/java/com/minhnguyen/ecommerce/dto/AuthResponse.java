package com.minhnguyen.ecommerce.dto;

public record AuthResponse(
        String token,
        String email,
        String name
){
}
