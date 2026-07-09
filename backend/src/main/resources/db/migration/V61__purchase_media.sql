-- Car picture metadata (file bytes live in Cloudflare R2; see automan.media.r2 config).
CREATE TABLE purchase_media (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id      BIGINT NOT NULL,
    chassis          VARCHAR(100) NOT NULL,
    file_key         VARCHAR(512) NOT NULL,
    original_name    VARCHAR(255) NULL,
    content_type     VARCHAR(64) NOT NULL,
    file_size        INT UNSIGNED NOT NULL,
    sort_order       SMALLINT NOT NULL DEFAULT 0,
    storage_provider ENUM('R2') NOT NULL DEFAULT 'R2',
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(120) NULL,
    deleted_at       TIMESTAMP NULL,
    CONSTRAINT fk_purchase_media_purchase
        FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
    CONSTRAINT uk_purchase_media_file_key UNIQUE (file_key),
    INDEX idx_purchase_media_purchase_id (purchase_id),
    INDEX idx_purchase_media_chassis (chassis),
    INDEX idx_purchase_media_purchase_sort (purchase_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
