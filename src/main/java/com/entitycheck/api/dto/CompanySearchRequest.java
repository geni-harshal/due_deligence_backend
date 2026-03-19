package com.entitycheck.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanySearchRequest(
    @NotBlank(message = "company_name is required")
    String company_name,
    String state
) {}
