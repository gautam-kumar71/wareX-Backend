package com.inventory.purchaseorder.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderStatusTest {

    @Test
    void transitionRules_matchStateMachine() {
        assertThat(PurchaseOrderStatus.DRAFT.canTransitionTo(PurchaseOrderStatus.SUBMITTED)).isTrue();
        assertThat(PurchaseOrderStatus.SUBMITTED.canTransitionTo(PurchaseOrderStatus.APPROVED)).isTrue();
        assertThat(PurchaseOrderStatus.APPROVED.canTransitionTo(PurchaseOrderStatus.RECEIVED)).isTrue();
        assertThat(PurchaseOrderStatus.CANCELLED.getAllowedTransitions()).isEmpty();
        assertThat(PurchaseOrderStatus.RECEIVED.canTransitionTo(PurchaseOrderStatus.CANCELLED)).isFalse();
    }
}
