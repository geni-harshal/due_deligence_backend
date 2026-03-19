package com.entitycheck.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "report_cache")
public class ReportCacheEntity {
  @Id
  @Column(name = "identifier", nullable = false, length = 64)
  private String identifier;

  @Column(name = "company_name")
  private String companyName;

  @Column(name = "payload_json", columnDefinition = "text", nullable = false)
  private String payloadJson;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
