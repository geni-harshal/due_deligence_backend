package com.entitycheck.api.dto;

public record CompanySearchResult(
    String name,
    String cin,
    String identifier_type,
    String address,
    String state,
    String status,
    String incorporation_date,
    String company_type,
    Double match_score
) {}
