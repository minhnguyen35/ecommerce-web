package com.minhnguyen.ecommerce.dto;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public record AuthResponse(
        String token,
        String email,
        String name
){
}
