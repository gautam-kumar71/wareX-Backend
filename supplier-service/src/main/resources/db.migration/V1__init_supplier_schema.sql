-- V1__init_supplier_schema.sql

CREATE TABLE suppliers (
                           id               BIGINT          NOT NULL AUTO_INCREMENT,
                           name             VARCHAR(255)    NOT NULL,
                           contact_person   VARCHAR(100)    NULL,
                           contact_email    VARCHAR(255)    NOT NULL,
                           contact_phone    VARCHAR(20)     NULL,
                           address          VARCHAR(500)    NULL,
                           city             VARCHAR(100)    NULL,
                           country          VARCHAR(100)    NOT NULL DEFAULT 'India',
                           gstin            VARCHAR(20)     NULL,
                           payment_terms    INT             NOT NULL DEFAULT 30,   -- days
                           credit_limit     DECIMAL(19,4)   NULL,
                           notes            TEXT            NULL,
                           active           BOOLEAN         NOT NULL DEFAULT TRUE,
                           created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                           CONSTRAINT pk_suppliers         PRIMARY KEY (id),
                           CONSTRAINT uq_supplier_email    UNIQUE (contact_email),
                           INDEX idx_supplier_name         (name),
                           INDEX idx_supplier_active       (active)
);
