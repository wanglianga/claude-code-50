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
@RequestMapping("/api")
public class WorkflowController extends BaseController {

    private final ShiftRepository shifts;
    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final OrderService orderService;

    public WorkflowController(ShiftRepository shifts, UserRepository users,
                              ComplaintRepository complaints, OrderService orderService) {
        this.shifts = shifts;
        this.complaints = complaints;
        this.users = users;
        this.orderService = orderService;
    }

    // ---------------- 用户与值班 ----------------
    @GetMapping("/users")
    public List<User> users(HttpServletRequest request) {
        currentUser(request);
        return users.findAll();
    }

    @GetMapping("/shifts")
    public List<PharmacistShift> shifts(HttpServletRequest request) {
        currentUser(request);
        return shifts.findAllByOrderByStartedAtDesc();
    }

    @GetMapping("/shifts/on-duty")
    public Object onDuty(HttpServletRequest request) {
        currentUser(request);
        return shifts.findByOnDutyTrue();
    }

    /** 药师上班（开始夜班）。 */
    @PostMapping("/shifts/start")
    public Object startShift(HttpServletRequest request) {
        User u = currentUser(request);
        requireRole(u, "PHARMACIST", "ADMIN");
        if (shifts.findFirstByPharmacistAndOnDutyTrue(u).isPresent())
            throw new ApiException("已在值班中");
        PharmacistShift s = new PharmacistShift();
        s.setPharmacist(u);
        s.setShiftDate(java.time.LocalDate.now().toString());
        s.setShiftType("NIGHT");
        s.setOnDuty(true);
        return Map.of("success", true, "data", shifts.save(s));
    }

    /** 夜间值班药师交接。 */
    @PostMapping("/shifts/handover")
    public Object handover(@RequestBody HandoverReq req, HttpServletRequest request) {
        User u = currentUser(request);
        requireRole(u, "PHARMACIST", "ADMIN");
        return Map.of("success", true, "data", orderService.handover(u, req));
    }

    // ---------------- 投诉台 ----------------
    @GetMapping("/complaints")
    public List<Complaint> listComplaints(@RequestParam(required = false) String status,
                                          HttpServletRequest request) {
        User u = currentUser(request);
        requireRole(u, "CUSTOMER_SERVICE", "ADMIN", "PHARMACIST");
        return status == null || status.isBlank()
                ? complaints.findAllByOrderByCreatedAtDesc()
                : complaints.findByStatusOrderByCreatedAtDesc(status);
    }

    @PostMapping("/complaints/{id}/handle")
    public Object handle(@PathVariable Long id, @RequestBody HandleComplaintReq req, HttpServletRequest request) {
        User u = currentUser(request);
        requireRole(u, "CUSTOMER_SERVICE", "ADMIN");
        return Map.of("success", true, "data", orderService.handleComplaint(u, id, req));
    }
}
