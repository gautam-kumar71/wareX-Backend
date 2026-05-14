-- V1__init_purchase_order_schema.sql
-- Purchase Order Service — owns this schema exclusively.

CREATE TABLE IF NOT EXISTS purchase_orders (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    version         BIGINT          NOT NULL DEFAULT 0,
    order_number    VARCHAR(30)     NOT NULL,
    supplier_id     BIGINT          NOT NULL,
    supplier_name   VARCHAR(255)    NOT NULL,
    warehouse_id    BIGINT          NOT NULL,
    status          ENUM(
        'DRAFT',
        'SUBMITTED',
        'APPROVED',
        'PARTIALLY_RECEIVED',
        'RECEIVED',
        'CANCELLED'
    )               NOT NULL DEFAULT 'DRAFT',
    total_amount    DECIMAL(19,4)   NOT NULL DEFAULT 0,
    notes           TEXT            NULL,
    created_by      VARCHAR(36)     NOT NULL,
    approved_by     VARCHAR(36)     NULL,
    cancelled_by    VARCHAR(36)     NULL,
    cancel_reason   VARCHAR(500)    NULL,
    expected_date   DATE            NULL,
    received_at     TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_purchase_orders   PRIMARY KEY (id),
    CONSTRAINT uq_order_number      UNIQUE (order_number),
    INDEX idx_po_supplier           (supplier_id),
    INDEX idx_po_warehouse          (warehouse_id),
    INDEX idx_po_status             (status),
    INDEX idx_po_created_by         (created_by)
);

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    version             BIGINT          NOT NULL DEFAULT 0,
    purchase_order_id   BIGINT          NOT NULL,
    product_id          BIGINT          NOT NULL,
    product_name        VARCHAR(255)    NOT NULL,
    product_sku         VARCHAR(100)    NOT NULL,
    ordered_qty         INT             NOT NULL,
    received_qty        INT             NOT NULL DEFAULT 0,
    unit_price          DECIMAL(19,4)   NOT NULL,
    line_total          DECIMAL(19,4)   NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_po_lines              PRIMARY KEY (id),
    CONSTRAINT fk_po_line_order         FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_ordered_qty_pos      CHECK (ordered_qty > 0),
    CONSTRAINT chk_received_qty_nn      CHECK (received_qty >= 0),
    CONSTRAINT chk_received_lte_ordered CHECK (received_qty <= ordered_qty),
    INDEX idx_pol_order                 (purchase_order_id),
    INDEX idx_pol_product               (product_id)
);
