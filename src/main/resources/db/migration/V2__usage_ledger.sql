CREATE TABLE usage_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_usage_ledger_request_event
        UNIQUE (request_id, event_type)
);