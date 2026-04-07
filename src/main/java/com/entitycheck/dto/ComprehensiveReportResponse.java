package com.entitycheck.dto;

import java.util.Map;

public class ComprehensiveReportResponse {

    private Map<String, Object> versionInfo;
    private Map<String, Object> metadata;
    private Map<String, Object> data;

    public ComprehensiveReportResponse() {}

    public ComprehensiveReportResponse(
            Map<String, Object> versionInfo,
            Map<String, Object> metadata,
            Map<String, Object> data
    ) {
        this.versionInfo = versionInfo;
        this.metadata = metadata;
        this.data = data;
    }

    public Map<String, Object> getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(Map<String, Object> versionInfo) {
        this.versionInfo = versionInfo;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}