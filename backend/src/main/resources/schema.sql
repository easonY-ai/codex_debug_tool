CREATE TABLE IF NOT EXISTS source_file (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    file_key TEXT NOT NULL,
    generation INTEGER NOT NULL,
    byte_offset INTEGER NOT NULL CHECK (byte_offset >= 0),
    anchor_hash TEXT NOT NULL,
    observed_size INTEGER NOT NULL,
    modified_at INTEGER NOT NULL,
    scanned_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS raw_jsonl_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL REFERENCES source_file(id),
    generation INTEGER NOT NULL,
    byte_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    content_hash TEXT NOT NULL,
    raw_bytes BLOB NOT NULL,
    raw_text TEXT NOT NULL,
    parse_status TEXT NOT NULL CHECK (parse_status IN ('VALID_JSON', 'INVALID_JSON', 'INVALID_UTF8')),
    event_type TEXT,
    event_time INTEGER,
    ingested_at INTEGER NOT NULL,
    UNIQUE (source_id, generation, byte_offset)
);

CREATE TABLE IF NOT EXISTS raw_hook_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    delivery_id TEXT NOT NULL UNIQUE,
    observed_at INTEGER NOT NULL,
    received_at INTEGER NOT NULL,
    forwarder_version TEXT NOT NULL,
    raw_json TEXT NOT NULL,
    parse_status TEXT NOT NULL CHECK (parse_status IN ('PENDING', 'NORMALIZED', 'UNKNOWN', 'FAILED')),
    error_code TEXT
);

CREATE TABLE IF NOT EXISTS normalization_job (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_kind TEXT NOT NULL CHECK (source_kind IN ('HOOK', 'JSONL', 'OTEL')),
    source_id INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    error_code TEXT,
    UNIQUE (source_kind, source_id)
);

CREATE INDEX IF NOT EXISTS idx_normalization_job_pending
    ON normalization_job(status, available_at, id);

CREATE TABLE IF NOT EXISTS hook_session (
    session_id TEXT PRIMARY KEY,
    transcript_path TEXT,
    started_at INTEGER,
    ended_at INTEGER,
    state TEXT NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'COMPLETED', 'INTERRUPTED')),
    last_observed_at INTEGER NOT NULL,
    version INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS hook_turn (
    session_id TEXT NOT NULL,
    turn_id TEXT NOT NULL,
    started_at INTEGER,
    ended_at INTEGER,
    state TEXT NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'COMPLETED', 'INTERRUPTED')),
    version INTEGER NOT NULL,
    PRIMARY KEY (session_id, turn_id)
);

CREATE TABLE IF NOT EXISTS hook_tool_call (
    session_id TEXT NOT NULL,
    turn_id TEXT NOT NULL,
    tool_use_id TEXT NOT NULL,
    tool_name TEXT,
    pre_observed_at INTEGER,
    post_observed_at INTEGER,
    state TEXT NOT NULL CHECK (state IN ('UNKNOWN', 'RUNNING', 'SUCCESS', 'FAILED', 'INTERRUPTED')),
    estimated_duration_ms INTEGER,
    duration_valid INTEGER NOT NULL CHECK (duration_valid IN (0, 1)),
    version INTEGER NOT NULL,
    PRIMARY KEY (session_id, turn_id, tool_use_id)
);

CREATE TABLE IF NOT EXISTS raw_otel_object (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_id TEXT NOT NULL,
    object_index INTEGER NOT NULL,
    signal_type TEXT NOT NULL CHECK (signal_type IN ('logs','traces','metrics')),
    trace_id TEXT,
    span_id TEXT,
    turn_id TEXT,
    call_id TEXT,
    object_kind TEXT,
    duration_ms INTEGER,
    event_time INTEGER,
    received_at INTEGER NOT NULL,
    raw_json TEXT NOT NULL,
    parse_status TEXT NOT NULL CHECK (parse_status IN ('VALID','UNKNOWN','FAILED')),
    UNIQUE(batch_id, object_index)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_otel_trace_span ON raw_otel_object(signal_type, trace_id, span_id)
    WHERE trace_id IS NOT NULL AND span_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS transcript_binding (
    id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL UNIQUE,
    configured_path TEXT, canonical_path TEXT, path_status TEXT NOT NULL,
    source_file_id INTEGER, session_meta_record_id INTEGER, jsonl_session_id TEXT,
    session_check_status TEXT NOT NULL, adapter_version TEXT, checked_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS jsonl_supplement (
    id INTEGER PRIMARY KEY AUTOINCREMENT, hook_node_type TEXT NOT NULL, hook_node_id TEXT NOT NULL,
    raw_record_id INTEGER NOT NULL, content_kind TEXT NOT NULL, call_id TEXT,
    mapping_level TEXT NOT NULL, evidence_json TEXT NOT NULL, adapter_version TEXT NOT NULL,
    content_text TEXT, UNIQUE(hook_node_type, hook_node_id, raw_record_id, content_kind)
);
CREATE TABLE IF NOT EXISTS unknown_fingerprint (
    id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint_sha256 TEXT NOT NULL UNIQUE,
    canonical_shape TEXT NOT NULL, source_kind TEXT NOT NULL, first_seen_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL, occurrence_count INTEGER NOT NULL, mapping_status TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS unknown_record (
    fingerprint_id INTEGER NOT NULL, raw_record_id INTEGER NOT NULL UNIQUE,
    FOREIGN KEY(fingerprint_id) REFERENCES unknown_fingerprint(id)
);
CREATE TABLE IF NOT EXISTS unknown_mapping (
    id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint_id INTEGER NOT NULL, version INTEGER NOT NULL,
    mapping_json TEXT NOT NULL, status TEXT NOT NULL, validation_error TEXT, created_at INTEGER NOT NULL,
    UNIQUE(fingerprint_id, version)
);
CREATE TABLE IF NOT EXISTS performance_alignment (
    id INTEGER PRIMARY KEY AUTOINCREMENT, hook_node_type TEXT NOT NULL, hook_node_id TEXT NOT NULL,
    otel_object_id INTEGER NOT NULL, level TEXT NOT NULL, evidence_json TEXT NOT NULL,
    algorithm_version TEXT NOT NULL, time_delta_ms INTEGER,
    UNIQUE(hook_node_type, hook_node_id, otel_object_id)
);
