package com.entitycheck.api.dto;

import java.util.List;

public record CompanySearchResponse(
    String query,
    String state_filter,
    List<CompanySearchResult> results,
    int total_found,
    String error,
    String highlight
) {}
