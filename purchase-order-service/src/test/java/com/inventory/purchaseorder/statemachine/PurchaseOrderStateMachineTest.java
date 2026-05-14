package com.inventory.purchaseorder.statemachine;

import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderStateMachineTest {

    private final PurchaseOrderStateMachine stateMachine = new PurchaseOrderStateMachine();

    @Test
    void validateTransition_allowsValidTransitions() {
        stateMachine.validateTransition(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.SUBMITTED);
        stateMachine.validateTransition(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void validateTransition_rejectsInvalidTransitions() {
        assertThatThrownBy(() -> stateMachine.validateTransition(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.RECEIVED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void resolveReceiptStatus_returnsExpectedState() {
        assertThat(stateMachine.resolveReceiptStatus(true)).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(stateMachine.resolveReceiptStatus(false)).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }
}
