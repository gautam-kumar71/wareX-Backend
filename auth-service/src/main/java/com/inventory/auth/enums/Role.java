package com.inventory.auth.enums;

import lombok.Getter;

@Getter
public enum Role {
    ADMIN("Full system access and user management"),
    INVENTORY_MANAGER("Oversee products, reports, and overall inventory health"),
    PURCHASE_OFFICER("Manage supplier relationships and procurement"),
    WAREHOUSE_STAFF("Manage day-to-day stock operations");

    private final String description;

    Role(String description) {
        this.description = description;
    }
}