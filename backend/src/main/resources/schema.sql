-- MySQL 8.4+ baseline. External IDs retain full text; invisible digest columns
-- provide case-sensitive full-value uniqueness without prefix truncation.
CREATE TABLE IF NOT EXISTS schema_migration (
    version INT PRIMARY KEY,
    applied_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS source_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    path LONGTEXT NOT NULL,
    file_key LONGTEXT NOT NULL,
    generation BIGINT NOT NULL,
    byte_offset BIGINT NOT NULL CHECK (byte_offset >= 0),
    anchor_hash VARCHAR(64) NOT NULL,
    observed_size BIGINT NOT NULL,
    modified_at BIGINT NOT NULL,
    scanned_at BIGINT NOT NULL,
    path_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(path, 256))) STORED INVISIBLE,
    UNIQUE (path_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS raw_jsonl_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    byte_offset BIGINT NOT NULL,
    end_offset BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    raw_bytes LONGBLOB NOT NULL,
    raw_text LONGTEXT NOT NULL,
    parse_status VARCHAR(64) NOT NULL CHECK (parse_status IN ('VALID_JSON', 'INVALID_JSON', 'INVALID_UTF8')),
    event_type LONGTEXT,
    event_time BIGINT,
    ingested_at BIGINT NOT NULL,
    UNIQUE (source_id, generation, byte_offset),
    FOREIGN KEY (source_id) REFERENCES source_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS raw_hook_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    delivery_id LONGTEXT NOT NULL,
    observed_at BIGINT NOT NULL,
    received_at BIGINT NOT NULL,
    forwarder_version LONGTEXT NOT NULL,
    raw_json LONGTEXT NOT NULL,
    parse_status VARCHAR(64) NOT NULL CHECK (parse_status IN ('PENDING', 'NORMALIZED', 'UNKNOWN', 'FAILED')),
    error_code LONGTEXT,
    delivery_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(delivery_id, 256))) STORED INVISIBLE,
    UNIQUE (delivery_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS normalization_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_kind VARCHAR(64) NOT NULL CHECK (source_kind IN ('HOOK', 'JSONL', 'OTEL')),
    source_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    available_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    error_code LONGTEXT,
    UNIQUE (source_kind, source_id),
    INDEX idx_normalization_job_pending (status, available_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS hook_session (
    session_id LONGTEXT NOT NULL,
    transcript_path LONGTEXT,
    started_at BIGINT,
    ended_at BIGINT,
    state VARCHAR(64) NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'COMPLETED', 'INTERRUPTED')),
    last_observed_at BIGINT NOT NULL,
    version INT NOT NULL,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    UNIQUE (session_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS hook_turn (
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    started_at BIGINT,
    ended_at BIGINT,
    state VARCHAR(64) NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'COMPLETED', 'INTERRUPTED')),
    version INT NOT NULL,
    UNIQUE (session_id_sha256, turn_id_sha256),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS hook_tool_call (
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    tool_use_id LONGTEXT NOT NULL,
    tool_name LONGTEXT,
    pre_observed_at BIGINT,
    post_observed_at BIGINT,
    state VARCHAR(64) NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'SUCCESS', 'FAILED', 'INTERRUPTED')),
    estimated_duration_ms BIGINT,
    duration_valid INT NOT NULL CHECK (duration_valid IN (0, 1)),
    version INT NOT NULL,
    UNIQUE (session_id_sha256, turn_id_sha256, tool_use_id_sha256),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    tool_use_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(tool_use_id, 256))) STORED INVISIBLE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS raw_otel_object (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id LONGTEXT NOT NULL,
    object_index INT NOT NULL,
    signal_type VARCHAR(64) NOT NULL CHECK (signal_type IN ('logs','traces','metrics')),
    trace_id LONGTEXT,
    span_id LONGTEXT,
    turn_id LONGTEXT,
    call_id LONGTEXT,
    object_kind LONGTEXT,
    duration_ms BIGINT,
    event_time BIGINT,
    received_at BIGINT NOT NULL,
    raw_json LONGTEXT NOT NULL,
    parse_status VARCHAR(64) NOT NULL CHECK (parse_status IN ('VALID','UNKNOWN','FAILED')),
    UNIQUE(batch_id_sha256, object_index),
    batch_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(batch_id, 256))) STORED INVISIBLE,
    trace_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(trace_id, 256))) STORED INVISIBLE,
    span_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(span_id, 256))) STORED INVISIBLE,
    UNIQUE INDEX idx_otel_trace_span (signal_type, trace_id_sha256, span_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS transcript_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id LONGTEXT NOT NULL,
    configured_path LONGTEXT, canonical_path LONGTEXT, path_status VARCHAR(64) NOT NULL,
    source_file_id BIGINT, session_meta_record_id BIGINT, jsonl_session_id LONGTEXT,
    session_check_status VARCHAR(64) NOT NULL, adapter_version LONGTEXT, checked_at BIGINT NOT NULL,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    UNIQUE (session_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS jsonl_supplement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, hook_node_type VARCHAR(64) NOT NULL, hook_node_id LONGTEXT NOT NULL,
    raw_record_id BIGINT NOT NULL, content_kind VARCHAR(64) NOT NULL, call_id LONGTEXT,
    mapping_level VARCHAR(64) NOT NULL, evidence_json LONGTEXT NOT NULL, adapter_version LONGTEXT NOT NULL,
    content_text LONGTEXT, UNIQUE(hook_node_type, hook_node_id_sha256, raw_record_id, content_kind),
    hook_node_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(hook_node_id, 256))) STORED INVISIBLE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS unknown_fingerprint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    canonical_shape LONGTEXT NOT NULL, source_kind VARCHAR(64) NOT NULL, first_seen_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL, occurrence_count BIGINT NOT NULL, mapping_status VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS unknown_record (
    fingerprint_id BIGINT NOT NULL, raw_record_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY(fingerprint_id) REFERENCES unknown_fingerprint(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS unknown_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, fingerprint_id BIGINT NOT NULL, version INT NOT NULL,
    mapping_json LONGTEXT NOT NULL, status VARCHAR(64) NOT NULL, validation_error LONGTEXT, created_at BIGINT NOT NULL,
    UNIQUE(fingerprint_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS performance_alignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, hook_node_type VARCHAR(64) NOT NULL, hook_node_id LONGTEXT NOT NULL,
    otel_object_id BIGINT NOT NULL, level VARCHAR(64) NOT NULL, evidence_json LONGTEXT NOT NULL,
    algorithm_version LONGTEXT NOT NULL, time_delta_ms BIGINT,
    UNIQUE(hook_node_type, hook_node_id_sha256, otel_object_id),
    hook_node_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(hook_node_id, 256))) STORED INVISIBLE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
