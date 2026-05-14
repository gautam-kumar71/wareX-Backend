package com.inventory.purchaseorder.statemachine;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

/**
 * Encapsulates all state transition logic for purchase orders.
 *
 * Why a separate class and not inline in the service?
 * The state machine rules are complex enough to warrant isolation —
 * it makes them easier to test in isolation and reason about independently.
 *
 * Allowed transitions:
 *   DRAFT              → SUBMITTED, CANCELLED
 *   SUBMITTED          → APPROVED, CANCELLED
 *   APPROVED           → PARTIALLY_RECEIVED, RECEIVED, CANCELLED
 *   PARTIALLY_RECEIVED → RECEIVED
 *   RECEIVED           → (terminal)
 *   CANCELLED          → (terminal)
 */
@Component
public class PurchaseOrderStateMachine {

    /**
     * Validates and enforces the transition from current to target status.
     *
     * @throws InvalidStatusTransitionException if the transition is not allowed
     */
    public void validateTransition(PurchaseOrderStatus current,
                                   PurchaseOrderStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(current, target);
        }
    }

    /**
     * Determines the correct next status after a stock receipt event,
     * based on whether all lines are now fully received.
     */
    public PurchaseOrderStatus resolveReceiptStatus(boolean fullyReceived) {
        return fullyReceived
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }
}