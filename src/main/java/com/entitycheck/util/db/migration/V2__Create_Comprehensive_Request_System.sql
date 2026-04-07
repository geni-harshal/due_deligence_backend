-- Flyway Migration: V2__Create_Comprehensive_Request_System.sql
-- Description: Create tables for comprehensive report request system
-- Author: Technical Architecture Team
-- Date: 2026-03-28

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "inet";

-- ============================================================
-- 1. COMPREHENSIVE REQUESTS TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS comprehensive_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_company_id BIGINT NOT NULL,
    company_cin VARCHAR(21) NOT NULL,
    company_name VARCHAR(500),
    company_type VARCHAR(100),
    request_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    request_type VARCHAR(50) NOT NULL DEFAULT 'COMPREHENSIVE_DETAILS',
    request_notes TEXT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_comp_req_client_company FOREIGN KEY (client_company_id) 
        REFERENCES client_companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_comp_req_created_by FOREIGN KEY (created_by) 
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_comp_req_status CHECK (request_status IN ('PENDING', 'FETCHING', 'FETCHED', 'FAILED', 'REQUIRES_RETRY')),
    CONSTRAINT chk_comp_req_type CHECK (request_type IN ('COMPREHENSIVE_DETAILS', 'FINANCIAL_SUMMARY', 'COMPLIANCE_CHECK'))
);

-- Create indexes for comprehensive_requests
CREATE INDEX idx_comp_req_client_company ON comprehensive_requests(client_company_id);
CREATE INDEX idx_comp_req_company_cin ON comprehensive_requests(company_cin);
CREATE INDEX idx_comp_req_status ON comprehensive_requests(request_status);
CREATE INDEX idx_comp_req_created_at ON comprehensive_requests(created_at DESC);
CREATE INDEX idx_comp_req_created_by ON comprehensive_requests(created_by);

-- ============================================================
-- 2. COMPREHENSIVE REQUEST RESPONSES TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS comprehensive_request_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    response_payload JSONB NOT NULL,
    api_endpoint VARCHAR(500) NOT NULL,
    api_version VARCHAR(20) NOT NULL,
    http_status_code SMALLINT NOT NULL,
    response_time_ms INTEGER,
    error_message TEXT,
    is_success BOOLEAN GENERATED ALWAYS AS 
        (http_status_code >= 200 AND http_status_code < 300) STORED,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_comp_resp_request FOREIGN KEY (request_id) 
        REFERENCES comprehensive_requests(id) ON DELETE CASCADE,
    CONSTRAINT chk_comp_resp_status CHECK (http_status_code >= 100 AND http_status_code < 600)
);

-- Create indexes for comprehensive_request_responses
CREATE INDEX idx_comp_resp_request_id ON comprehensive_request_responses(request_id DESC);
CREATE INDEX idx_comp_resp_is_success ON comprehensive_request_responses(is_success);
CREATE INDEX idx_comp_resp_fetched_at ON comprehensive_request_responses(fetched_at DESC);
CREATE INDEX idx_comp_resp_http_status ON comprehensive_request_responses(http_status_code);

-- Create JSONB index for better query performance
CREATE INDEX idx_comp_resp_payload_gin ON comprehensive_request_responses USING GIN (response_payload);

-- ============================================================
-- 3. COMPREHENSIVE AUDIT LOGS TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS comprehensive_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID,
    audit_action VARCHAR(100) NOT NULL,
    actor_id BIGINT,
    actor_email VARCHAR(255) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    audit_details JSONB NOT NULL,
    client_ip_address INET,
    user_agent TEXT,
    request_correlation_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_comp_audit_request FOREIGN KEY (request_id) 
        REFERENCES comprehensive_requests(id) ON DELETE SET NULL,
    CONSTRAINT fk_comp_audit_actor FOREIGN KEY (actor_id) 
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_comp_audit_action CHECK (audit_action IN (
        'REQUEST_CREATED', 'RESPONSE_RECEIVED', 'RESPONSE_FAILED',
        'REFRESH_INITIATED', 'REFRESH_COMPLETED', 'RESPONSE_VIEWED',
        'AUDIT_QUERIED', 'REQUEST_DELETED', 'REQUEST_ACCESSED'
    ))
);

-- Create indexes for comprehensive_audit_logs
CREATE INDEX idx_comp_audit_request_id ON comprehensive_audit_logs(request_id);
CREATE INDEX idx_comp_audit_actor_id ON comprehensive_audit_logs(actor_id);
CREATE INDEX idx_comp_audit_action ON comprehensive_audit_logs(audit_action);
CREATE INDEX idx_comp_audit_created_at ON comprehensive_audit_logs(created_at DESC);
CREATE INDEX idx_comp_audit_correlation ON comprehensive_audit_logs(request_correlation_id);
CREATE INDEX idx_comp_audit_actor_email ON comprehensive_audit_logs(actor_email);
CREATE INDEX idx_comp_audit_ip_address ON comprehensive_audit_logs(client_ip_address);

-- Create JSONB index for audit details
CREATE INDEX idx_comp_audit_details_gin ON comprehensive_audit_logs USING GIN (audit_details);

-- ============================================================
-- 4. FUNCTION FOR AUTO-UPDATING UPDATED_AT TIMESTAMP
-- ============================================================
CREATE OR REPLACE FUNCTION update_comprehensive_requests_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 5. TRIGGER FOR AUTO-UPDATING UPDATED_AT
-- ============================================================
DROP TRIGGER IF EXISTS trigger_comp_req_updated_at ON comprehensive_requests;

CREATE TRIGGER trigger_comp_req_updated_at
BEFORE UPDATE ON comprehensive_requests
FOR EACH ROW
EXECUTE FUNCTION update_comprehensive_requests_updated_at();

-- ============================================================
-- 6. AUDIT LOG TRIGGER (IMMUTABLE - Prevent updates)
-- ============================================================
CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable and cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_prevent_audit_modification ON comprehensive_audit_logs;

CREATE TRIGGER trigger_prevent_audit_modification
BEFORE UPDATE OR DELETE ON comprehensive_audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_audit_log_modification();

-- ============================================================
-- 7. AUDIT LOG TRIGGER (IMMUTABLE - Prevent deletes)
-- ============================================================
CREATE OR REPLACE FUNCTION prevent_response_deletion()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Responses are immutable and cannot be deleted. They can only be replaced by creating a new response.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_prevent_response_deletion ON comprehensive_request_responses;

CREATE TRIGGER trigger_prevent_response_deletion
BEFORE DELETE ON comprehensive_request_responses
FOR EACH ROW
EXECUTE FUNCTION prevent_response_deletion();

-- ============================================================
-- 8. GRANTS (RBAC)
-- ============================================================
-- These would be adjusted based on actual user/role setup
-- GRANT SELECT, INSERT, UPDATE ON comprehensive_requests TO app_user;
-- GRANT SELECT, INSERT ON comprehensive_request_responses TO app_user;
-- GRANT SELECT, INSERT ON comprehensive_audit_logs TO app_user;

-- ============================================================
-- ROLLBACK SCRIPT (V2__rollback)
-- ============================================================
-- To rollback this migration:
-- 
-- DROP TRIGGER IF EXISTS trigger_prevent_response_deletion ON comprehensive_request_responses;
-- DROP TRIGGER IF EXISTS trigger_prevent_audit_modification ON comprehensive_audit_logs;
-- DROP TRIGGER IF EXISTS trigger_comp_req_updated_at ON comprehensive_requests;
-- DROP FUNCTION IF EXISTS prevent_response_deletion();
-- DROP FUNCTION IF EXISTS prevent_audit_log_modification();
-- DROP FUNCTION IF EXISTS update_comprehensive_requests_updated_at();
-- DROP TABLE IF EXISTS comprehensive_audit_logs;
-- DROP TABLE IF EXISTS comprehensive_request_responses;
-- DROP TABLE IF EXISTS comprehensive_requests;

-- ============================================================
-- MIGRATION COMPLETE
-- ============================================================
