package com.inventory.warehouse.enums;

public enum AdjustmentReason {
    CYCLE_COUNT,        // periodic stock count correction
    DAMAGED_GOODS,      // items damaged in warehouse
    EXPIRED,            // expired products removed
    THEFT,              // shrinkage due to theft
    FOUND_STOCK,        // items found that were previously unaccounted
    SUPPLIER_RETURN,    // returned to supplier
    QUALITY_REJECTION,  // failed QC inspection
    DATA_CORRECTION,    // fixing a data entry error (admin use only)
    OTHER
}