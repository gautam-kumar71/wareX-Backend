package com.inventory.purchaseorder.enums;

import java.util.Set;

public enum PurchaseOrderStatus {

    DRAFT,
    SUBMITTED,
    APPROVED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    /**
     * Defines the valid next states from the current state.
     * Any transition not listed here is illegal and will throw
     * InvalidStatusTransitionException.
     *
     * State machine:
     *
     *   DRAFT ──────────────────────────────────────► CANCELLED
     *     │
     *     ▼
     *   SUBMITTED ──────────────────────────────────► CANCELLED
     *     │
     *     ▼
     *   APPROVED ───────────────────────────────────► CANCELLED
     *     │
     *     ▼
     *   PARTIALLY_RECEIVED (some lines done)
     *     │
     *     ▼
     *   RECEIVED (all lines fully received) — terminal
     *
     *   CANCELLED — terminal (cannot be un-cancelled)
     */
    public Set<PurchaseOrderStatus> getAllowedTransitions() {
        return switch (this) {
            case DRAFT              -> Set.of(SUBMITTED, CANCELLED);
            case SUBMITTED          -> Set.of(APPROVED, CANCELLED);
            case APPROVED           -> Set.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED);
            case PARTIALLY_RECEIVED -> Set.of(RECEIVED);
            case RECEIVED           -> Set.of();   // terminal
            case CANCELLED          -> Set.of();   // terminal
        };
    }

    public boolean canTransitionTo(PurchaseOrderStatus target) {
        return getAllowedTransitions().contains(target);
    }
}