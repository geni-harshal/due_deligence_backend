package com.entitycheck.dto;

public record UserDto(
        Long id,
        String email,
        String fullName,
        String role,
        Long clientCompanyId,
        String clientCompanyName,
        boolean isActive
) {}