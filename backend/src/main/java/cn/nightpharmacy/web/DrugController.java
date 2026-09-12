package cn.nightpharmacy.web;

import cn.nightpharmacy.domain.Drug;
import cn.nightpharmacy.domain.User;
import cn.nightpharmacy.repo.DrugRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drugs")
public class DrugController extends BaseController {

    private final DrugRepository drugs;

    public DrugController(DrugRepository drugs) {
        this.drugs = drugs;
    }

    @GetMapping
    public List<Drug> list(@RequestParam(required = false) String q) {
        if (q != null && !q.isBlank()) return drugs.findByNameContaining(q.trim());
        return drugs.findAll();
    }

    @GetMapping("/{id}")
    public Drug get(@PathVariable Long id) {
        return drugs.findById(id).orElseThrow(() -> new ApiException(404, "药品不存在"));
    }

    /** 仓管维护库存/批号/效期。 */
    @PutMapping("/{id}")
    public Drug update(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        User u = currentUser(req);
        requireRole(u, "WAREHOUSE", "ADMIN");
        Drug d = drugs.findById(id).orElseThrow(() -> new ApiException(404, "药品不存在"));
        if (body.containsKey("stock")) d.setStock(Integer.valueOf(String.valueOf(body.get("stock"))));
        if (body.containsKey("batchNo")) d.setBatchNo(String.valueOf(body.get("batchNo")));
        if (body.containsKey("expiryDate") && body.get("expiryDate") != null)
            d.setExpiryDate(LocalDate.parse(String.valueOf(body.get("expiryDate"))));
        if (body.containsKey("price")) d.setPrice(new java.math.BigDecimal(String.valueOf(body.get("price"))));
        return drugs.save(d);
    }
}
