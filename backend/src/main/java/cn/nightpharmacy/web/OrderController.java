package cn.nightpharmacy.web;

import cn.nightpharmacy.domain.*;
import cn.nightpharmacy.repo.*;
import cn.nightpharmacy.service.OrderService;
import cn.nightpharmacy.service.Requests.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController extends BaseController {

    private final OrderService service;
    private final OrderRepository orders;
    private final UserRepository users;

    public OrderController(OrderService service, OrderRepository orders, UserRepository users) {
        this.service = service;
        this.orders = orders;
        this.users = users;
    }

    private Map<String, Object> ok(DispenseOrder o) {
        return Map.of("success", true, "data", service.detail(o.getId()));
    }

    // ---------------- 患者提交 / 列表 / 详情 ----------------
    @PostMapping
    public Map<String, Object> submit(@RequestBody SubmitOrder req, HttpServletRequest request) {
        User u = currentUser(request);
        requireRole(u, "PATIENT", "ADMIN");
        return ok(service.submit(u, req));
    }

    @GetMapping
    public Object list(HttpServletRequest request) {
        User u = currentUser(request);
        List<DispenseOrder> list = "PATIENT".equals(u.getRole())
                ? orders.findByPatientOrderByCreatedAtDesc(u)
                : orders.findAllByOrderByCreatedAtDesc();
        return Map.of("success", true, "data", list);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, HttpServletRequest request) {
        return Map.of("success", true, "data", service.detail(id, currentUser(request)));
    }

    // ---------------- 药师：审方/退回/联系患者/医生核实/暂停/恢复/转线下 ----------------
    @PostMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable Long id, @RequestBody ReviewReq req, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST", "ADMIN");
        return ok(service.review(u, id, req));
    }

    @PostMapping("/{id}/return")
    public Map<String, Object> returnBack(@PathVariable Long id, @RequestBody Map<String, String> body,
                                          HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST");
        return ok(service.returnForSupplement(u, id, body.getOrDefault("reason", "处方图片不清")));
    }

    @PostMapping("/{id}/resubmit")
    public Map<String, Object> resubmit(@PathVariable Long id, @RequestBody Map<String, String> body,
                                        HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PATIENT");
        return ok(service.patientResubmit(u, id, body.get("prescriptionImagePath"), body.get("supplement")));
    }

    @PostMapping("/{id}/contact")
    public Map<String, Object> contact(@PathVariable Long id, @RequestBody Map<String, String> body,
                                       HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST", "CUSTOMER_SERVICE");
        return ok(service.contactPatient(u, id, body.getOrDefault("note", "")));
    }

    @PostMapping("/{id}/doctor-verify")
    public Map<String, Object> doctorVerify(@PathVariable Long id, @RequestBody DoctorVerifyReq req,
                                            HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST");
        return ok(service.submitDoctorVerify(u, id, req));
    }

    @PostMapping("/{id}/pause")
    public Map<String, Object> pause(@PathVariable Long id, @RequestBody DecisionReq req, HttpServletRequest r) {
        return ok(service.pauseOrder(currentUser(r), id, req.reason()));
    }

    @PostMapping("/{id}/resume")
    public Map<String, Object> resume(@PathVariable Long id, @RequestBody DecisionReq req, HttpServletRequest r) {
        return ok(service.resumeOrder(currentUser(r), id, req.reason()));
    }

    @PostMapping("/{id}/offline")
    public Map<String, Object> offline(@PathVariable Long id, @RequestBody DecisionReq req, HttpServletRequest r) {
        return ok(service.offlineReferral(currentUser(r), id, req.reason()));
    }

    // ---------------- 医保：重试 / 回退（药师或收银） ----------------
    @PostMapping("/{id}/insurance/retry")
    public Map<String, Object> insuranceRetry(@PathVariable Long id, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST", "CASHIER", "ADMIN");
        return ok(service.runInsurance(u, id));
    }

    @PostMapping("/{id}/insurance/rollback")
    public Map<String, Object> rollback(@PathVariable Long id, @RequestBody DecisionReq req, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST", "CASHIER");
        return ok(service.insuranceRollback(u, id, req.reason()));
    }

    // ---------------- 收银：支付（发票抬头） ----------------
    @PostMapping("/{id}/pay")
    public Map<String, Object> pay(@PathVariable Long id, @RequestBody PayReq req, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "CASHIER", "ADMIN");
        return ok(service.pay(u, id, req));
    }

    // ---------------- 缺药替代协商 ----------------
    @GetMapping("/{id}/items/{itemId}/candidates")
    public Object candidates(@PathVariable Long id, @PathVariable Long itemId, HttpServletRequest r) {
        User u = currentUser(r);
        return Map.of("success", true, "data", service.substitutionCandidates(id, itemId, u));
    }

    @PostMapping("/{id}/negotiations")
    public Object startNegotiation(@PathVariable Long id, @RequestBody NegotiateReq req, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PHARMACIST", "ADMIN");
        return Map.of("success", true, "data", service.startNegotiation(u, id, req));
    }

    @PostMapping("/{id}/negotiations/{nid}/decision")
    public Object negotiationDecision(@PathVariable Long id, @PathVariable Long nid,
                                      @RequestBody NegotiationDecisionReq req, HttpServletRequest r) {
        User u = currentUser(r);
        // 患者本人确认，或客服经患者电话确认后代录
        requireRole(u, "PATIENT", "CUSTOMER_SERVICE", "ADMIN");
        return Map.of("success", true, "data", service.decideNegotiation(u, id, nid, req));
    }

    // ---------------- 仓管：替代/缺药移除/出库 ----------------
    @PostMapping("/{id}/substitute")
    public Map<String, Object> substitute(@PathVariable Long id, @RequestBody SubstituteReq req,
                                          HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "WAREHOUSE", "PHARMACIST", "ADMIN");
        return ok(service.substitute(u, id, req));
    }

    @PostMapping("/{id}/items/{itemId}/remove")
    public Map<String, Object> removeItem(@PathVariable Long id, @PathVariable Long itemId,
                                          @RequestBody Map<String, String> body, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "WAREHOUSE", "PHARMACIST");
        return ok(service.removeItem(u, id, itemId, body.getOrDefault("reason", "缺药无替代")));
    }

    @PostMapping("/{id}/outbound")
    public Map<String, Object> outbound(@PathVariable Long id, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "WAREHOUSE", "ADMIN");
        return ok(service.outbound(u, id));
    }

    // ---------------- 配送信息 / 冷链 / 签收 ----------------
    @PostMapping("/{id}/address")
    public Map<String, Object> address(@PathVariable Long id, @RequestBody AddressReq req, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PATIENT", "CUSTOMER_SERVICE", "RIDER", "ADMIN");
        return ok(service.changeAddress(u, id, req));
    }

    @PostMapping("/{id}/cold-confirm")
    public Map<String, Object> coldConfirm(@PathVariable Long id, @RequestBody ColdConfirmReq req,
                                           HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "RIDER", "WAREHOUSE", "ADMIN");
        return ok(service.coldConfirm(u, id, req));
    }

    @PostMapping("/{id}/deliver")
    public Map<String, Object> deliver(@PathVariable Long id, @RequestBody DeliverReq req, HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "RIDER", "WAREHOUSE", "CUSTOMER_SERVICE", "ADMIN");
        return ok(service.deliver(u, id, req));
    }

    // ---------------- 投诉 ----------------
    @PostMapping("/{id}/complaints")
    public Map<String, Object> complaint(@PathVariable Long id, @RequestBody ComplaintReq req,
                                         HttpServletRequest r) {
        User u = currentUser(r);
        requireRole(u, "PATIENT", "CUSTOMER_SERVICE", "ADMIN");
        return Map.of("success", true, "data", service.fileComplaint(u, id, req));
    }
}
