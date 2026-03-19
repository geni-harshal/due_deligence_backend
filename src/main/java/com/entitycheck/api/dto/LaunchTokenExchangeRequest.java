package com.entitycheck.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LaunchTokenExchangeRequest(
    @NotBlank String launch_token
) {}
