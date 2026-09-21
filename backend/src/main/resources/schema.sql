-- Trace Lens MySQL 8.4+ empty-schema baseline 2.
-- Deployment applies this file explicitly. The application only validates schema_metadata.
-- Long external identifiers retain source text and use SHA-256 generated columns for indexes.

CREATE TABLE IF NOT EXISTS schema_metadata (
    singleton_id TINYINT PRIMARY KEY CHECK (singleton_id = 1),
    baseline_version INT NOT NULL CHECK (baseline_version = 2),
    installed_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
INSERT INTO schema_metadata(singleton_id, baseline_version, installed_at)
VALUES (1, 2, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)
ON DUPLICATE KEY UPDATE singleton_id = singleton_id;

CREATE TABLE IF NOT EXISTS execution_raw_hook_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    delivery_id LONGTEXT NOT NULL,
    observed_at BIGINT NOT NULL,
    received_at BIGINT NOT NULL,
    forwarder_version LONGTEXT NOT NULL,
    raw_json LONGTEXT NOT NULL,
    parse_status VARCHAR(64) NOT NULL CHECK (parse_status IN ('PENDING', 'NORMALIZED', 'UNKNOWN', 'FAILED')),
    error_code VARCHAR(128),
    delivery_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(delivery_id, 256))) STORED INVISIBLE,
    UNIQUE (delivery_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS execution_normalization_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_kind VARCHAR(64) NOT NULL CHECK (source_kind = 'HOOK'),
    source_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    error_code VARCHAR(128),
    UNIQUE (source_kind, source_id),
    INDEX idx_execution_normalization_pending (status, available_at, id),
    FOREIGN KEY (source_id) REFERENCES execution_raw_hook_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS execution_session (
    session_id LONGTEXT NOT NULL,
    transcript_path LONGTEXT,
    started_at BIGINT,
    ended_at BIGINT,
    state VARCHAR(64) NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'COMPLETED', 'INTERRUPTED')),
    last_observed_at BIGINT NOT NULL,
    version INT NOT NULL CHECK (version > 0),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    UNIQUE (session_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS execution_turn (
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    title LONGTEXT,
    model LONGTEXT,
    working_directory LONGTEXT,
    started_at BIGINT,
    ended_at BIGINT,
    state VARCHAR(64) NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'COMPLETED', 'INTERRUPTED')),
    version INT NOT NULL CHECK (version > 0),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    UNIQUE (session_id_sha256, turn_id_sha256),
    INDEX idx_execution_turn_started (started_at),
    FOREIGN KEY (session_id_sha256) REFERENCES execution_session(session_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS execution_tool_call (
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    tool_use_id LONGTEXT NOT NULL,
    tool_name LONGTEXT,
    pre_observed_at BIGINT,
    post_observed_at BIGINT,
    state VARCHAR(64) NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'SUCCESS', 'FAILED', 'INTERRUPTED')),
    estimated_duration_ms BIGINT,
    duration_valid BOOLEAN NOT NULL,
    version INT NOT NULL CHECK (version > 0),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    tool_use_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(tool_use_id, 256))) STORED INVISIBLE,
    UNIQUE (session_id_sha256, turn_id_sha256, tool_use_id_sha256),
    FOREIGN KEY (session_id_sha256, turn_id_sha256)
        REFERENCES execution_turn(session_id_sha256, turn_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS execution_change (
    change_sequence BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type VARCHAR(32) NOT NULL CHECK (aggregate_type IN ('SESSION', 'TURN', 'TOOL_CALL')),
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT,
    tool_use_id LONGTEXT,
    aggregate_revision INT NOT NULL CHECK (aggregate_revision > 0),
    change_kind VARCHAR(64) NOT NULL,
    changed_at BIGINT NOT NULL,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    INDEX idx_execution_change_turn (session_id_sha256, turn_id_sha256, change_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS transcript (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    path LONGTEXT NOT NULL,
    configured_path LONGTEXT,
    canonical_path LONGTEXT,
    file_key LONGTEXT NOT NULL,
    generation BIGINT NOT NULL CHECK (generation >= 0),
    byte_offset BIGINT NOT NULL CHECK (byte_offset >= 0),
    anchor_hash VARCHAR(64) NOT NULL,
    observed_size BIGINT NOT NULL CHECK (observed_size >= 0),
    modified_at BIGINT NOT NULL,
    scanned_at BIGINT NOT NULL,
    path_status VARCHAR(64) NOT NULL DEFAULT 'SAFE',
    meta_session_id LONGTEXT,
    session_meta_item_id BIGINT,
    session_check_status VARCHAR(64) NOT NULL DEFAULT 'MISSING_META',
    adapter_version LONGTEXT,
    checked_at BIGINT NOT NULL DEFAULT 0,
    revision INT NOT NULL DEFAULT 1 CHECK (revision > 0),
    path_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(path, 256))) STORED INVISIBLE,
    meta_session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(meta_session_id, 256))) STORED INVISIBLE,
    UNIQUE (path_sha256),
    INDEX idx_transcript_meta_session (meta_session_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS transcript_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transcript_id BIGINT NOT NULL,
    transcript_path LONGTEXT NOT NULL,
    generation BIGINT NOT NULL CHECK (generation >= 0),
    byte_offset BIGINT NOT NULL CHECK (byte_offset >= 0),
    end_offset BIGINT NOT NULL CHECK (end_offset >= 0),
    content_hash VARCHAR(64) NOT NULL,
    raw_bytes LONGBLOB NOT NULL,
    raw_text LONGTEXT NOT NULL,
    parse_status VARCHAR(64) NOT NULL CHECK (parse_status IN ('VALID_JSON', 'INVALID_JSON', 'INVALID_UTF8')),
    item_type LONGTEXT,
    event_time BIGINT,
    session_id LONGTEXT,
    turn_id LONGTEXT,
    call_id LONGTEXT,
    adapter_version LONGTEXT,
    ingested_at BIGINT NOT NULL,
    transcript_path_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(transcript_path, 256))) STORED INVISIBLE,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    UNIQUE (transcript_path_sha256, generation, byte_offset),
    INDEX idx_transcript_item_source (transcript_id, generation, byte_offset),
    INDEX idx_transcript_item_turn (session_id_sha256, turn_id_sha256, id),
    FOREIGN KEY (transcript_id) REFERENCES transcript(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS transcript_unknown_fingerprint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    canonical_shape LONGTEXT NOT NULL,
    source_kind VARCHAR(64) NOT NULL CHECK (source_kind = 'JSONL'),
    first_seen_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    occurrence_count BIGINT NOT NULL CHECK (occurrence_count >= 0),
    mapping_status VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS transcript_unknown_item (
    fingerprint_id BIGINT NOT NULL,
    transcript_item_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY (fingerprint_id) REFERENCES transcript_unknown_fingerprint(id),
    FOREIGN KEY (transcript_item_id) REFERENCES transcript_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS transcript_unknown_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fingerprint_id BIGINT NOT NULL,
    version INT NOT NULL CHECK (version > 0),
    mapping_json LONGTEXT NOT NULL,
    status VARCHAR(64) NOT NULL,
    validation_error LONGTEXT,
    created_at BIGINT NOT NULL,
    UNIQUE (fingerprint_id, version),
    FOREIGN KEY (fingerprint_id) REFERENCES transcript_unknown_fingerprint(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS transcript_change (
    change_sequence BIGINT PRIMARY KEY AUTO_INCREMENT,
    transcript_path LONGTEXT NOT NULL,
    transcript_revision INT NOT NULL CHECK (transcript_revision > 0),
    transcript_item_id BIGINT,
    session_id LONGTEXT,
    turn_id LONGTEXT,
    change_kind VARCHAR(64) NOT NULL,
    changed_at BIGINT NOT NULL,
    transcript_path_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(transcript_path, 256))) STORED INVISIBLE,
    INDEX idx_transcript_change_path (transcript_path_sha256, change_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS telemetry_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id LONGTEXT NOT NULL,
    object_index INT NOT NULL CHECK (object_index >= 0),
    signal_type VARCHAR(64) NOT NULL CHECK (signal_type IN ('logs', 'traces', 'metrics')),
    trace_id LONGTEXT,
    span_id LONGTEXT,
    session_id LONGTEXT,
    turn_id LONGTEXT,
    call_id LONGTEXT,
    object_kind LONGTEXT,
    duration_ms BIGINT,
    event_time BIGINT,
    received_at BIGINT NOT NULL,
    raw_json LONGTEXT NOT NULL,
    parse_status VARCHAR(64) NOT NULL CHECK (parse_status IN ('VALID', 'UNKNOWN', 'FAILED')),
    batch_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(batch_id, 256))) STORED INVISIBLE,
    trace_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(trace_id, 256))) STORED INVISIBLE,
    span_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(span_id, 256))) STORED INVISIBLE,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    UNIQUE (batch_id_sha256, object_index),
    UNIQUE INDEX idx_telemetry_protocol_identity (signal_type, trace_id_sha256, span_id_sha256),
    INDEX idx_telemetry_turn (session_id_sha256, turn_id_sha256, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS telemetry_change (
    change_sequence BIGINT PRIMARY KEY AUTO_INCREMENT,
    telemetry_record_id BIGINT NOT NULL,
    session_id LONGTEXT,
    turn_id LONGTEXT,
    change_kind VARCHAR(64) NOT NULL,
    changed_at BIGINT NOT NULL,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    INDEX idx_telemetry_change_turn (session_id_sha256, turn_id_sha256, change_sequence),
    FOREIGN KEY (telemetry_record_id) REFERENCES telemetry_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS trace_transcript_evidence_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transcript_item_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL CHECK (target_type IN ('TURN', 'TOOL_CALL')),
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    tool_use_id LONGTEXT,
    content_kind VARCHAR(64) NOT NULL,
    call_id LONGTEXT,
    level VARCHAR(64) NOT NULL CHECK (level IN ('EXACT', 'BOUNDED', 'UNMATCHED')),
    evidence_json LONGTEXT NOT NULL,
    algorithm_version LONGTEXT NOT NULL,
    content_text LONGTEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'STALE')),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    tool_use_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(COALESCE(tool_use_id, ''), 256))) STORED INVISIBLE,
    algorithm_version_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(algorithm_version, 256))) STORED INVISIBLE,
    UNIQUE (transcript_item_id, target_type, session_id_sha256, turn_id_sha256, tool_use_id_sha256, algorithm_version_sha256),
    INDEX idx_trace_transcript_turn (session_id_sha256, turn_id_sha256, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS trace_telemetry_alignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    telemetry_record_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL CHECK (target_type IN ('TURN', 'TOOL_CALL')),
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    tool_use_id LONGTEXT,
    level VARCHAR(64) NOT NULL CHECK (level IN ('BOUNDED', 'UNMATCHED')),
    evidence_json LONGTEXT NOT NULL,
    algorithm_version LONGTEXT NOT NULL,
    time_delta_ms BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'STALE')),
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    tool_use_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(COALESCE(tool_use_id, ''), 256))) STORED INVISIBLE,
    algorithm_version_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(algorithm_version, 256))) STORED INVISIBLE,
    UNIQUE (telemetry_record_id, target_type, session_id_sha256, turn_id_sha256, tool_use_id_sha256, algorithm_version_sha256),
    INDEX idx_trace_telemetry_turn (session_id_sha256, turn_id_sha256, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS trace_turn_view (
    session_id LONGTEXT NOT NULL,
    turn_id LONGTEXT NOT NULL,
    source_revision BIGINT NOT NULL,
    view_json LONGTEXT NOT NULL,
    projected_at BIGINT NOT NULL,
    session_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(session_id, 256))) STORED INVISIBLE,
    turn_id_sha256 BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(turn_id, 256))) STORED INVISIBLE,
    UNIQUE (session_id_sha256, turn_id_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS trace_projection_checkpoint (
    change_source VARCHAR(64) NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    last_change_sequence BIGINT NOT NULL CHECK (last_change_sequence >= 0),
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (change_source, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
