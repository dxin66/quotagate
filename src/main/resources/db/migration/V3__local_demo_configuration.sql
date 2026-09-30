INSERT INTO tenant (id, name, status, created_at)
VALUES (1, 'Local Demo', 'ACTIVE', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

INSERT INTO api_key
    (id, tenant_id, key_prefix, key_hash, status, expires_at, created_at)
VALUES
    (1, 1, 'qg_sk_test',
     'd9ad46b64ef3a0e2151449ce7db2bb4bf0c7aa815178c519bb251dbe2f088230',
     'ACTIVE', NULL, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id), status = VALUES(status), expires_at = NULL;

INSERT INTO model_route
    (id, model_alias, provider, upstream_model, priority, enabled, input_price, output_price)
VALUES
    (1, 'gpt-standard', 'mock', 'mock', 1, TRUE, 0, 0)
ON DUPLICATE KEY UPDATE
    provider = VALUES(provider), upstream_model = VALUES(upstream_model),
    priority = VALUES(priority), enabled = VALUES(enabled);
