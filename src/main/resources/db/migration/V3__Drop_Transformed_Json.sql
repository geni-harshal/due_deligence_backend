-- Flyway Migration: V3__Drop_Transformed_Json.sql
-- Description: Remove transformedJson columns from raw comprehensive data and cache tables
-- Justification: 
--   - Backend API now uses only snapshotData (raw JSON)
--   - Transformations happen on-demand (runModels, generatePdf)
--   - Reduces DB size, write latency, and schema complexity
-- Author: Technical Architecture Team
-- Date: 2026-04-15

-- ============================================================
-- Drop transformedJson from raw_comprehensive_data_versions
-- ============================================================
ALTER TABLE raw_comprehensive_data_versions
DROP COLUMN IF EXISTS transformed_json;

-- ============================================================
-- Drop transformedReportJson from provider_search_snapshots
-- ============================================================
ALTER TABLE provider_search_snapshots
DROP COLUMN IF EXISTS transformed_report_json;

-- ============================================================
-- Verification
-- ============================================================
-- After this migration:
-- - raw_comprehensive_data_versions contains ONLY: raw_json
-- - provider_search_snapshots contains ONLY: raw_results_json
-- - All transformations are done on-demand by services
-- ============================================================
