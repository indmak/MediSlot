package com.medislot.web;

import com.medislot.entity.Payment;
import com.medislot.exception.BusinessException;
import com.medislot.service.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 支付（当前为模拟/沙箱）。真实网关接入后，这里改为跳转收银台 + 异步回调。
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 收银台（模拟支付页）。 */
    @GetMapping("/{paymentNo}")
    public String payPage(@PathVariable String paymentNo, Authentication authentication, Model model) {
        Payment payment = paymentService.getByNo(paymentNo);
        if (!payment.getAppointment().getPatient().getPhone().equals(authentication.getName())) {
            return "redirect:/appointments/my";
        }
        model.addAttribute("payment", payment);
        return "payment/pay";
    }

    /** 模拟支付成功。 */
    @PostMapping("/{paymentNo}/pay")
    public String pay(@PathVariable String paymentNo, Authentication authentication, RedirectAttributes ra) {
        try {
            paymentService.pay(paymentNo, authentication.getName());
            ra.addFlashAttribute("message", "支付成功");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/appointments/my";
    }

    /** 模拟支付失败。 */
    @PostMapping("/{paymentNo}/fail")
    public String fail(@PathVariable String paymentNo, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "模拟支付失败，请重试");
        return "redirect:/payments/" + paymentNo;
    }
}
