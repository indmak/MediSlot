package com.medislot.service;

import com.medislot.entity.Appointment;
import com.medislot.entity.AppointmentStatus;
import com.medislot.entity.Payment;
import com.medislot.entity.PaymentStatus;
import com.medislot.entity.Schedule;
import com.medislot.exception.BusinessException;
import com.medislot.repository.AppointmentRepository;
import com.medislot.repository.PaymentRepository;
import com.medislot.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付业务（独立订单表）。
 *
 * <p>当前为「模拟支付」：不接任何真实网关，{@code pay(...)} 直接把订单标记为已支付。
 * 将来接入支付宝沙箱/正式时，只需把 {@code pay(...)} 换成网关下单 + 异步回调，
 * 其余（超时作废、退款、状态流转）不变。
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final ConsultationService consultationService;
    private final long expiryMinutes;

    public PaymentService(PaymentRepository paymentRepository,
                          AppointmentRepository appointmentRepository,
                          ScheduleRepository scheduleRepository,
                          ConsultationService consultationService,
                          @Value("${medislot.payment.expiry-minutes:30}") long expiryMinutes) {
        this.paymentRepository = paymentRepository;
        this.appointmentRepository = appointmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.consultationService = consultationService;
        this.expiryMinutes = expiryMinutes;
    }

    /** 预约成功后创建待支付订单。 */
    @Transactional
    public Payment createForAppointment(Appointment appointment) {
        Payment payment = new Payment(
                generatePaymentNo(),
                appointment,
                appointment.getRegistrationFee(),
                LocalDateTime.now().plusMinutes(expiryMinutes));
        payment.setMethod("MOCK");
        Payment saved = paymentRepository.save(payment);
        appointment.setPayment(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Payment getByNo(String paymentNo) {
        return paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BusinessException("支付订单不存在"));
    }

    /**
     * 模拟支付成功（沙箱/模拟网关）。
     */
    @Transactional
    public Payment pay(String paymentNo, String patientPhone) {
        Payment payment = getByNo(paymentNo);
        assertOwner(payment, patientPhone);
        if (payment.getStatus() != PaymentStatus.UNPAID) {
            throw new BusinessException("当前订单状态不可支付");
        }
        if (payment.getExpiresAt().isBefore(LocalDateTime.now())) {
            expire(payment);
            throw new BusinessException("订单已超时失效，请重新预约");
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setMethod("MOCK");
        Payment saved = paymentRepository.save(payment);
        // 支付成功 → 开启诊前咨询群聊
        consultationService.getOrCreateGroup(payment.getAppointment().getId());
        return saved;
    }

    /** 取消预约时处理支付：已支付→退款，未支付→作废。 */
    @Transactional
    public void voidOrRefund(Appointment appointment) {
        paymentRepository.findByAppointmentId(appointment.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PAID) {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            } else if (payment.getStatus() == PaymentStatus.UNPAID) {
                payment.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(payment);
            }
        });
    }

    /** 超时未支付的订单作废，并把预约取消、释放号源。 */
    @Transactional
    public int expireOverdue() {
        List<Payment> overdue = paymentRepository.findByStatusAndExpiresAtBefore(
                PaymentStatus.UNPAID, LocalDateTime.now());
        overdue.forEach(this::expire);
        return overdue.size();
    }

    private void expire(Payment payment) {
        payment.setStatus(PaymentStatus.EXPIRED);
        paymentRepository.save(payment);

        Appointment appointment = payment.getAppointment();
        if (appointment.getStatus() == AppointmentStatus.PENDING) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            releaseSlot(appointment.getSchedule());
            appointmentRepository.save(appointment);
        }
    }

    private void releaseSlot(Schedule schedule) {
        if (schedule.getBookedCount() != null && schedule.getBookedCount() > 0) {
            schedule.setBookedCount(schedule.getBookedCount() - 1);
            scheduleRepository.save(schedule);
        }
    }

    private void assertOwner(Payment payment, String patientPhone) {
        if (!payment.getAppointment().getPatient().getPhone().equals(patientPhone)) {
            throw new BusinessException("无权操作该订单");
        }
    }

    private String generatePaymentNo() {
        return "P" + System.currentTimeMillis()
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1, 1000));
    }
}
