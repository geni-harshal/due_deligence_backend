package com.entitycheck.api.dto;

import java.util.List;

public record SsoSessionResponse(
    String source,
    String user_email,
    String tenant_id,
    List<String> roles,
    String expires_at
) {}
