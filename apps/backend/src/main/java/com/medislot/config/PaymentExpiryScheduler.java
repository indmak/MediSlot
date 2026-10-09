package com.medislot.config;

import com.medislot.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时作废超时未支付的订单（默认 30 分钟），并释放号源。
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    private final PaymentService paymentService;

    public PaymentExpiryScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 每 60 秒扫描一次。 */
    @Scheduled(fixedDelayString = "60000", initialDelayString = "30000")
    public void expireOverduePayments() {
        int count = paymentService.expireOverdue();
        if (count > 0) {
            log.info("[payment] 作废超时未支付订单 {} 笔，已释放号源", count);
        }
    }
}
