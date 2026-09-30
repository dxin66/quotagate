CREATE TABLE tenant (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(128) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        created_at DATETIME NOT NULL
);

CREATE TABLE api_key (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         tenant_id BIGINT NOT NULL,
                         key_prefix VARCHAR(24) NOT NULL,
                         key_hash CHAR(64) NOT NULL,
                         status VARCHAR(32) NOT NULL,
                         expires_at DATETIME NULL,
                         created_at DATETIME NOT NULL,
                         CONSTRAINT uk_api_key_hash UNIQUE (key_hash),
                         CONSTRAINT fk_api_key_tenant
                             FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE TABLE model_route (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                             model_alias VARCHAR(128) NOT NULL,
                             provider VARCHAR(64) NOT NULL,
                             upstream_model VARCHAR(128) NOT NULL,
                             priority INT NOT NULL,
                             enabled BOOLEAN NOT NULL,
                             input_price DECIMAL(18, 8) NOT NULL DEFAULT 0,
                             output_price DECIMAL(18, 8) NOT NULL DEFAULT 0,
                             INDEX idx_model_route_alias (model_alias, enabled, priority)
);