-- V1__init_stock_movement_schema.sql
-- Stock Movement Service — APPEND-ONLY.
-- No UPDATE or DELETE operations are ever issued against stock_movements.
-- This table is the immutable audit trail for all stock changes.

CREATE TABLE stock_movements (
                                 id              BIGINT          NOT NULL AUTO_INCREMENT,
    -- event_id from Kafka payload — unique constraint prevents duplicate inserts
    -- This is our idempotency key: if the same Kafka event is delivered twice,
    -- the second insert will fail the unique constraint and be silently ignored.
                                 event_id        VARCHAR(36)     NOT NULL,
                                 product_id      BIGINT          NOT NULL,
                                 warehouse_id    BIGINT          NOT NULL,
                                 movement_type   ENUM(
                        'RECEIPT',
                        'TRANSFER_IN',
                        'TRANSFER_OUT',
                        'ADJUSTMENT_ADD',
                        'ADJUSTMENT_SUB',
                        'SALE',
                        'RESERVATION',
                        'RESERVATION_RELEASE'
                    )               NOT NULL,
                                 quantity_delta  INT             NOT NULL,   -- positive = in, negative = out
                                 quantity_after  INT             NOT NULL,   -- snapshot of qty after this movement
                                 reference_id    VARCHAR(36)     NULL,        -- PO id, order id, transfer id, etc.
                                 reference_type  VARCHAR(50)     NULL,        -- PURCHASE_ORDER, SALE_ORDER, TRANSFER, etc.
                                 notes           VARCHAR(500)    NULL,
                                 occurred_at     TIMESTAMP       NOT NULL,    -- when it happened in source system
                                 recorded_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP, -- when we recorded it

                                 CONSTRAINT pk_stock_movements   PRIMARY KEY (id),
                                 CONSTRAINT uq_event_id          UNIQUE (event_id),   -- idempotency guard
                                 INDEX idx_sm_product            (product_id),
                                 INDEX idx_sm_warehouse          (warehouse_id),
                                 INDEX idx_sm_occurred           (occurred_at),
                                 INDEX idx_sm_type               (movement_type),
                                 INDEX idx_sm_product_warehouse  (product_id, warehouse_id),
                                 INDEX idx_sm_reference          (reference_id, reference_type)
);
