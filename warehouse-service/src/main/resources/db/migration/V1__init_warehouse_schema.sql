-- V1__init_warehouse_schema.sql
-- Warehouse Service — owns this schema exclusively.
-- No other microservice touches these tables directly.

CREATE TABLE warehouses (
                            id          BIGINT        NOT NULL AUTO_INCREMENT,
                            name        VARCHAR(100)  NOT NULL,
                            location    VARCHAR(255)  NOT NULL,
                            city        VARCHAR(100)  NOT NULL,
                            country     VARCHAR(100)  NOT NULL DEFAULT 'India',
                            active      BOOLEAN       NOT NULL DEFAULT TRUE,
                            created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                            CONSTRAINT pk_warehouses      PRIMARY KEY (id),
                            CONSTRAINT uq_warehouse_name  UNIQUE (name)
);

-- stock_levels: one row per (warehouse, product) pair.
-- quantity is NEVER allowed to go below 0 — enforced at DB AND service level.
-- version column drives optimistic locking via @Version in JPA.
CREATE TABLE stock_levels (
                              id              BIGINT  NOT NULL AUTO_INCREMENT,
                              warehouse_id    BIGINT  NOT NULL,
                              product_id      BIGINT  NOT NULL,
                              quantity        INT     NOT NULL DEFAULT 0,
                              reserved_qty    INT     NOT NULL DEFAULT 0,   -- qty reserved for pending orders
                              reorder_point   INT     NOT NULL DEFAULT 0,   -- triggers LOW_STOCK alert
                              max_capacity    INT     NULL,                  -- triggers OVERSTOCK alert
                              version         INT     NOT NULL DEFAULT 0,   -- JPA optimistic lock column
                              created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                              CONSTRAINT pk_stock_levels         PRIMARY KEY (id),
                              CONSTRAINT uq_warehouse_product    UNIQUE (warehouse_id, product_id),
                              CONSTRAINT fk_stock_warehouse      FOREIGN KEY (warehouse_id)
                                  REFERENCES warehouses (id) ON DELETE RESTRICT,
                              CONSTRAINT chk_quantity_non_neg    CHECK (quantity >= 0),
                              CONSTRAINT chk_reserved_non_neg   CHECK (reserved_qty >= 0),
                              CONSTRAINT chk_reorder_non_neg    CHECK (reorder_point >= 0),
                              INDEX idx_stock_warehouse          (warehouse_id),
                              INDEX idx_stock_product            (product_id),
                              INDEX idx_stock_low                (quantity, reorder_point)
);