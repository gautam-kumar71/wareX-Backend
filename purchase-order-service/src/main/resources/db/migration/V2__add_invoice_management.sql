-- V2__add_invoice_management.sql

CREATE TABLE IF NOT EXISTS invoices (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    invoice_number  VARCHAR(50)     NOT NULL,
    po_id           BIGINT          NOT NULL,
    supplier_id     BIGINT          NOT NULL,
    supplier_name   VARCHAR(255)    NOT NULL,
    amount          DECIMAL(19,4)   NOT NULL,
    due_date        DATE            NOT NULL,
    status          ENUM(
        'PENDING',
        'APPROVED',
        'PAID',
        'OVERDUE',
        'CANCELLED'
    )               NOT NULL DEFAULT 'PENDING',
    notes           TEXT            NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_invoices          PRIMARY KEY (id),
    CONSTRAINT uq_invoice_number    UNIQUE (invoice_number),
    CONSTRAINT fk_invoice_po        FOREIGN KEY (po_id)
        REFERENCES purchase_orders (id) ON DELETE CASCADE,
    INDEX idx_inv_po                (po_id),
    INDEX idx_inv_supplier          (supplier_id),
    INDEX idx_inv_status            (status),
    INDEX idx_inv_number            (invoice_number)
);
