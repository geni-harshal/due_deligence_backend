package com.entitycheck.dto;

public record LoginResponse(
        String token,
        UserDto user
) {}
