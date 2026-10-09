package com.medislot.entity;

/**
 * 支付状态。
 */
public enum PaymentStatus {
    UNPAID("待支付"),
    PAID("已支付"),
    REFUNDED("已退款"),
    EXPIRED("已失效"),
    FAILED("支付失败");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
