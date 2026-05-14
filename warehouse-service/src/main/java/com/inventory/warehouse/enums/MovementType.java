package com.inventory.warehouse.enums;

public enum MovementType {
    RECEIPT,         // stock received from a purchase order
    TRANSFER_IN,     // stock transferred in from another warehouse
    TRANSFER_OUT,    // stock transferred out to another warehouse
    ADJUSTMENT_ADD,  // manual positive adjustment (found stock, correction)
    ADJUSTMENT_SUB,  // manual negative adjustment (damaged, shrinkage)
    SALE,            // stock consumed by a sale/order
    RESERVATION,     // stock reserved for a pending order
    RESERVATION_RELEASE // reserved stock released (order cancelled)
}