package cn.nightpharmacy.service;

import cn.nightpharmacy.domain.*;
import cn.nightpharmacy.repo.*;
import cn.nightpharmacy.service.Requests.*;
import cn.nightpharmacy.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 配药单协同核心：提交→自动核验→审方→（退回/医生核实）→医保→费用→支付
 * →出库（缺药/临期/替代）→冷链配送→签收归档→次日投诉追溯；含药师交接、医保回退。
 * 每一次继续/暂停/转线下的决策都写入 OrderEvent 说明原因。
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderEventRepository events;
    private final DrugRepository drugs;
    private final UserRepository users;
    private final ShiftRepository shifts;
    private final ComplaintRepository complaints;
    private final InsuranceService insurance;

    @Value("${app.night-fee-rate}") private BigDecimal nightFeeRate;
    @Value("${app.night-fee-min}") private BigDecimal nightFeeMin;
    @Value("${app.chronic-annual-quota}") private BigDecimal chronicQuota;
    @Value("${app.chronic-used-default}") private BigDecimal chronicUsedDefault;

    /** 药师可处理的在途状态 */
    private static final List<String> HANDOVER_STATUSES =
            List.of("SUBMITTED", "PENDING_SUPPLEMENT", "PHARMACIST_REVIEW", "DOCTOR_VERIFY",
                    "INSURANCE_CHECK", "WAIT_PAYMENT", "PAUSED");
    private static final List<String> ACTIVE_STATUSES =
            List.of("SUBMITTED", "PENDING_SUPPLEMENT", "PHARMACIST_REVIEW", "DOCTOR_VERIFY",
                    "INSURANCE_CHECK", "WAIT_PAYMENT", "PAID", "PICKING", "DELIVERING", "PAUSED");

    public OrderService(OrderRepository orders, OrderItemRepository items, OrderEventRepository events,
                        DrugRepository drugs, UserRepository users, ShiftRepository shifts,
                        ComplaintRepository complaints, InsuranceService insurance) {
        this.orders = orders; this.items = items; this.events = events;
        this.drugs = drugs; this.users = users; this.shifts = shifts;
        this.complaints = complaints; this.insurance = insurance;
    }

    // ============================================================
    // 患者提交 + 平台自动核验
    // ============================================================
    @Transactional
    public DispenseOrder submit(User patient, SubmitOrder req) {
        if (req.items() == null || req.items().isEmpty())
            throw new ApiException("请至少选择一种药品");

        DispenseOrder o = new DispenseOrder();
        o.setOrderNo("NP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + String.format("%03d", new Random().nextInt(1000)));
        o.setPatient(patient);
        o.setSymptoms(req.symptoms());
        o.setAllergies(req.allergies());
        o.setCurrentMedication(req.currentMedication());
        o.setPrescriptionImagePath(req.prescriptionImagePath());
        o.setPrescribingDoctor(req.prescribingDoctor());
        o.setDoctorHospital(req.doctorHospital());
        LocalDate rxDate = req.prescriptionDate() != null ? req.prescriptionDate() : LocalDate.now();
        o.setPrescriptionDate(rxDate);
        o.setPrescriptionValidUntil(rxDate.plusDays(7)); // 急诊处方 7 日有效
        o.setInsuranceNo(req.insuranceNo());
        o.setChronicFlag(req.chronicFlag());
        o.setNeedsDelivery(req.needsDelivery());
        o.setAddress(req.address());
        o.setContactPhone(req.contactPhone());
        o.setRecipient(req.recipient());
        o.setStatus("SUBMITTED");
        o.setDecision("CONTINUE");
        orders.save(o);

        boolean cold = false;
        for (ItemReq ir : req.items()) {
            Drug d = drugs.findById(ir.drugId())
                    .orElseThrow(() -> new ApiException("药品不存在: " + ir.drugId()));
            OrderItem it = new OrderItem();
            it.setOrder(o);
            it.setDrug(d);
            it.setQuantity(ir.quantity() == null ? 1 : ir.quantity());
            it.setDosage(ir.dosage());
            it.setUnitPrice(d.getPrice());
            it.setFulfillStatus("PENDING");
            items.save(it);
            if (d.isColdChain()) cold = true;
        }
        o.setColdChainRequired(cold);

        List<String> warnings = runPrecheck(o);
        StringJoiner sj = new StringJoiner("；", "平台自动核验：", "。");
        warnings.forEach(sj::add);
        recordEvent(o, "SUBMIT", "CONTINUE", sj.toString(), patient, null, "SUBMITTED");

        // 管制药品超量 / 无值班药师等硬性问题：暂停或转线下，均记录原因
        if (warnings.stream().anyMatch(w -> w.contains("无夜间值班药师"))) {
            offline(o, "当前无夜间值班药师在岗，无法完成处方审核，建议改往 24 小时急诊药房或线下复诊", patient);
            recordEvent(o, "OFFLINE", "OFFLINE",
                    "夜间无值班药师，不能保证双人复核与用药安全，本单改为线下复诊", patient,
                    "SUBMITTED", "OFFLINE_REFERRAL");
            return o;
        }
        if (warnings.stream().anyMatch(w -> w.contains("管制药品超量"))) {
            pause(o, "管制药品超出夜间限购数量", patient);
            recordEvent(o, "PAUSE", "PAUSE",
                    "管制药品超量触发限购规则，暂停配药，待药师核用处方案后决定继续或转线下",
                    patient, "SUBMITTED", "PAUSED");
        }
        return o;
    }

    /** 提交时的五项自动核验：处方有效期 / 管制类别 / 库存批号 / 冷藏 / 药师值班。 */
    private List<String> runPrecheck(DispenseOrder o) {
        List<String> w = new ArrayList<>();
        // 1) 处方有效期
        if (o.getPrescriptionValidUntil() != null && LocalDate.now().isAfter(o.getPrescriptionValidUntil())) {
            w.add("处方已过有效期（有效至 " + o.getPrescriptionValidUntil() + "），需医生重新开具");
        } else {
            w.add("处方在有效期内（至 " + o.getPrescriptionValidUntil() + "）");
        }
        List<OrderItem> its = items.findByOrderOrderByIdAsc(o);
        // 2) 管制类别 + 限购
        int controlledQty = 0;
        for (OrderItem it : its) {
            Drug d = it.getDrug();
            if ("CONTROLLED".equals(d.getControlCategory())) controlledQty += it.getQuantity();
        }
        if (controlledQty > 0) {
            if (controlledQty > 2) w.add("管制药品超量：本单含管制药品 " + controlledQty
                    + " 盒/板，夜间限购 2 份且须急诊医生电话核实");
            else w.add("含管制药品 " + controlledQty + " 份，须双人核验并经急诊医生电话核实");
        } else {
            w.add("药品管制类别核验通过");
        }
        // 3) 库存批号 + 临期
        List<String> stockMsgs = new ArrayList<>();
        for (OrderItem it : its) {
            Drug d = it.getDrug();
            if (d.getStock() < it.getQuantity()) {
                stockMsgs.add("《" + d.getName() + "》库存不足（需" + it.getQuantity() + "/存" + d.getStock() + "）");
            } else {
                String near = d.getExpiryDate() != null
                        && !d.getExpiryDate().isAfter(LocalDate.now().plusDays(90)) ? "，批号临期" : "";
                stockMsgs.add("《" + d.getName() + "》批号" + d.getBatchNo()
                        + "/效期" + d.getExpiryDate() + near + "/库存" + d.getStock());
            }
        }
        if (stockMsgs.stream().anyMatch(s -> s.contains("库存不足"))) {
            w.add("库存批号预警：" + String.join("；", stockMsgs));
        } else {
            w.add("库存批号核验：" + String.join("；", stockMsgs));
        }
        // 4) 冷藏
        if (o.isColdChainRequired()) {
            w.add("本单含 2-8℃ 冷藏药品，配送须使用冷链箱并上传温控照片");
        } else {
            w.add("无冷藏要求");
        }
        // 5) 药师值班
        long onDuty = shifts.findByOnDutyTrue().stream()
                .filter(s -> "PHARMACIST".equals(s.getPharmacist().getRole())).count();
        if (onDuty == 0) w.add("无夜间值班药师在岗");
        else w.add("夜间值班药师 " + onDuty + " 名在岗");
        return w;
    }

    // ============================================================
    // 药师审方
    // ============================================================
    @Transactional
    public DispenseOrder review(User pharmacist, Long orderId, ReviewReq req) {
        DispenseOrder o = mustGet(orderId);
        if (!List.of("SUBMITTED", "PHARMACIST_REVIEW", "DOCTOR_VERIFY", "PAUSED").contains(o.getStatus())
                && !"INSURANCE_CHECK".equals(o.getStatus())) {
            throw new ApiException("当前状态(" + o.getStatus() + ")不能审方");
        }
        requireOnDutyPharmacist(pharmacist);

        List<String> findings = buildReviewFindings(o);
        boolean controlled = items.findByOrderOrderByIdAsc(o).stream()
                .anyMatch(it -> "CONTROLLED".equals(it.getDrug().getControlCategory()));
        if (controlled && (o.getDoctorVerifiedAt() == null)) {
            // 管制药必须先电话核实急诊医生
            o.setStatus("DOCTOR_VERIFY");
            o.setDecision("PAUSE");
            o.setPharmacist(pharmacist);
            recordEvent(o, "REVIEW", "PAUSE",
                    "审方发现含管制药品且急诊医生尚未电话核实，暂停进入医生核实环节。系统审方提示："
                            + String.join("；", findings), pharmacist, null, "DOCTOR_VERIFY");
            return o;
        }
        o.setPharmacist(pharmacist);
        StringJoiner opinion = new StringJoiner("\n");
        opinion.add("【医生签名】" + o.getPrescribingDoctor() + "（" + o.getDoctorHospital() + "）"
                + (o.getDoctorVerifiedAt() != null ? "，已电话核实：" + o.getDoctorVerifiedByName() : ""));
        findings.forEach(opinion::add);
        if (req.opinion() != null && !req.opinion().isBlank()) opinion.add("【药师意见】" + req.opinion());
        o.setPharmacistOpinion(opinion.toString());
        o.setPharmacistSignedAt(LocalDateTime.now());
        o.setStatus("INSURANCE_CHECK");
        o.setDecision("CONTINUE");
        // 默认条目通过
        for (OrderItem it : items.findByOrderOrderByIdAsc(o)) {
            if ("PENDING".equals(it.getFulfillStatus())) {
                it.setFulfillStatus("CONFIRMED");
                it.setReviewNote("审方通过：剂量/用法与处方一致");
            }
        }
        recordEvent(o, "REVIEW", "CONTINUE",
                "药师 " + pharmacist.getDisplayName() + " 审方通过，签名归档。审方要点："
                        + String.join("；", findings), pharmacist, null, "INSURANCE_CHECK");
        // 自动触发医保核验
        return runInsurance(pharmacist, orderId);
    }

    /** 生成医生签名/剂量/禁忌/重复用药/替代药审方要点。 */
    private List<String> buildReviewFindings(DispenseOrder o) {
        List<String> l = new ArrayList<>();
        List<OrderItem> its = items.findByOrderOrderByIdAsc(o);
        // 剂量
        List<String> dose = new ArrayList<>();
        for (OrderItem it : its) {
            dose.add(it.getDrug().getName() + " ×" + it.getQuantity()
                    + (it.getDosage() != null ? "（" + it.getDosage() + "）" : "（处方未标注用法，需核实）"));
        }
        l.add("【剂量用法】" + String.join("；", dose));
        // 禁忌 vs 过敏史
        List<String> contra = new ArrayList<>();
        String allergies = o.getAllergies() == null ? "" : o.getAllergies();
        for (OrderItem it : its) {
            Drug d = it.getDrug();
            if (d.getContraindication() != null && !d.getContraindication().isBlank()) {
                boolean hit = allergies.lines().anyMatch(a -> !a.isBlank()
                        && d.getContraindication().contains(a.trim()));
                contra.add(d.getName() + "：禁忌(" + d.getContraindication() + ")" + (hit ? "⚠与过敏史冲突" : ""));
            }
        }
        l.add(contra.isEmpty() ? "【禁忌】未登记特殊禁忌" : "【禁忌】" + String.join("；", contra));
        // 重复用药
        List<String> dup = new ArrayList<>();
        Map<String, Integer> nameCount = new HashMap<>();
        for (OrderItem it : its) nameCount.merge(it.getDrug().getName(), 1, Integer::sum);
        nameCount.forEach((n, c) -> { if (c > 1) dup.add(n + " 重复 " + c + " 条"); });
        String curMed = o.getCurrentMedication() == null ? "" : o.getCurrentMedication();
        for (OrderItem it : its) {
            String n = it.getDrug().getName();
            if (curMed.contains(n)) dup.add(n + " 与患者既往用药重复，需防超量");
        }
        l.add(dup.isEmpty() ? "【重复用药】未发现重复用药" : "【重复用药】" + String.join("；", dup));
        // 替代药
        List<String> alt = new ArrayList<>();
        for (OrderItem it : its) {
            if (it.getDrug().getAlternative() != null && !it.getDrug().getAlternative().isBlank()) {
                alt.add(it.getDrug().getName() + " → 可替代：" + it.getDrug().getAlternative());
            }
        }
        l.add(alt.isEmpty() ? "【替代药】无" : "【替代药建议】" + String.join("；", alt));
        return l;
    }

    /** 处方字迹/图片不清：药师退回患者补充（暂停配药）。 */
    @Transactional
    public DispenseOrder returnForSupplement(User pharmacist, Long orderId, String reason) {
        DispenseOrder o = mustGet(orderId);
        o.setPharmacist(pharmacist);
        String why = "处方字迹/图片不清，退回患者补充材料：" + reason;
        pause(o, why, pharmacist);
        o.setStatus("PENDING_SUPPLEMENT");
        recordEvent(o, "RETURN", "PAUSE", why + "。配药暂停，待患者重新上传清晰处方/补充说明后继续",
                pharmacist, o.getPrevStatus(), "PENDING_SUPPLEMENT");
        return o;
    }

    /** 患者补充材料后重新提交。 */
    @Transactional
    public DispenseOrder patientResubmit(User patient, Long orderId, String newImagePath, String supplement) {
        DispenseOrder o = mustGet(orderId);
        if (!"PENDING_SUPPLEMENT".equals(o.getStatus()))
            throw new ApiException("仅退回待补充状态可补充材料");
        if (newImagePath != null && !newImagePath.isBlank()) o.setPrescriptionImagePath(newImagePath);
        if (supplement != null && !supplement.isBlank())
            o.setSymptoms((o.getSymptoms() == null ? "" : o.getSymptoms() + "\n【补充】" + supplement));
        o.setStatus("SUBMITTED");
        o.setDecision("CONTINUE");
        recordEvent(o, "SUBMIT", "CONTINUE",
                "患者已补充处方材料/说明：" + supplement + (newImagePath != null ? "（已重新上传图片）" : ""),
                patient, "PENDING_SUPPLEMENT", "SUBMITTED");
        return o;
    }

    /** 药师联系患者确认关键药品（记录沟通，不改变流程结论）。 */
    @Transactional
    public DispenseOrder contactPatient(User pharmacist, Long orderId, String note) {
        DispenseOrder o = mustGet(orderId);
        o.setPharmacist(pharmacist);
        recordEvent(o, "CONTACT", "INFO",
                "药师就关键药品电话联系患者/家属确认：" + note, pharmacist, null, null);
        return o;
    }

    // ============================================================
    // 急诊医生电话核实（管制药品必须）
    // ============================================================
    @Transactional
    public DispenseOrder submitDoctorVerify(User pharmacist, Long orderId, DoctorVerifyReq req) {
        DispenseOrder o = mustGet(orderId);
        o.setDoctorVerifiedByName(req.doctorName());
        o.setDoctorVerifiedPhone(req.phone());
        o.setDoctorVerifyNote(req.note());
        o.setDoctorVerifiedAt(LocalDateTime.now());
        if (o.getPharmacist() == null) o.setPharmacist(pharmacist);
        recordEvent(o, "DOCTOR_VERIFY", "CONTINUE",
                "已电话联系急诊医生 " + req.doctorName() + "（" + req.phone() + "）核实处方及管制药品用药依据："
                        + req.note() + "。核实通过，继续配药",
                pharmacist, o.getStatus(), "PHARMACIST_REVIEW");
        o.setStatus("PHARMACIST_REVIEW");
        o.setDecision("CONTINUE");
        return o;
    }

    // ============================================================
    // 医保核验 + 回退 + 费用拆分
    // ============================================================
    @Transactional
    public DispenseOrder runInsurance(User actor, Long orderId) {
        DispenseOrder o = mustGet(orderId);
        if (o.getPharmacistSignedAt() == null)
            throw new ApiException("药师尚未完成审方签名");
        o.setStatus("INSURANCE_CHECK");
        var r = insurance.verify(o.getInsuranceNo(), o.isChronicFlag(), chronicUsedDefault, chronicQuota);
        if (r.interfaceError()) {
            o.setInsuranceStatus("ERROR");
            o.setInsuranceMessage(r.message());
            o.setDecision("PAUSE");
            recordEvent(o, "INSURANCE", "PAUSE", r.message()
                    + "。配药暂停：可重试接口，或经患者同意后医保回退（全自费）", actor, null, "INSURANCE_CHECK");
            return o;
        }
        if (!r.approved()) {
            o.setInsuranceStatus("REJECTED");
            o.setInsuranceMessage(r.message());
            o.setDecision("PAUSE");
            recordEvent(o, "INSURANCE", "PAUSE", r.message()
                    + "。配药暂停：等待收银与患者确认是否医保回退为全自费", actor, null, "INSURANCE_CHECK");
            return o;
        }
        o.setInsuranceStatus("APPROVED");
        o.setInsuranceMessage(r.message());
        o.setInsuranceCheckedAt(LocalDateTime.now());
        calculateAmounts(o, r);
        o.setStatus("WAIT_PAYMENT");
        o.setDecision("CONTINUE");
        recordEvent(o, "INSURANCE", "CONTINUE", r.message()
                + "。已生成费用拆分：药品费 " + money(o.getDrugTotal()) + " 元，统筹 " + money(o.getPoolingPay())
                + " 元，门诊慢病额度 " + money(o.getChronicPay()) + " 元，夜间服务费 "
                + money(o.getNightFee()) + " 元，患者自费 " + money(o.getSelfPay()) + " 元",
                actor, null, "WAIT_PAYMENT");
        return o;
    }

    /** 医保接口异常/拒付后的回退：经患者同意转全自费。 */
    @Transactional
    public DispenseOrder insuranceRollback(User actor, Long orderId, String reason) {
        DispenseOrder o = mustGet(orderId);
        String prev = o.getInsuranceStatus();
        o.setInsuranceStatus("ROLLED_BACK");
        o.setInsuranceMessage("医保回退：" + reason);
        o.setPoolingPay(BigDecimal.ZERO);
        o.setChronicPay(BigDecimal.ZERO);
        o.setDrugTotal(sumDrugTotal(o));
        BigDecimal nf = nightFee(o.getDrugTotal());
        o.setNightFee(nf);
        o.setSelfPay(o.getDrugTotal().add(nf));
        o.setTotalAmount(o.getSelfPay());
        o.setStatus("WAIT_PAYMENT");
        o.setDecision("CONTINUE");
        recordEvent(o, "ROLLBACK", "CONTINUE",
                "因「" + o.getInsuranceMessage() + "」（原医保状态 " + prev + "），经患者确认执行医保回退：统筹/慢病支付清零，"
                        + "全部费用转为患者自费 " + money(o.getSelfPay()) + " 元（含夜间服务费 " + money(nf)
                        + " 元），继续配药。原因：" + reason, actor, "INSURANCE_CHECK", "WAIT_PAYMENT");
        return o;
    }

    private void calculateAmounts(DispenseOrder o, InsuranceService.InsuranceResult r) {
        BigDecimal drugTotal = BigDecimal.ZERO;
        BigDecimal eligible = BigDecimal.ZERO; // 医保目录内费用
        for (OrderItem it : items.findByOrderOrderByIdAsc(o)) {
            if ("REMOVED".equals(it.getFulfillStatus())) continue;
            boolean sub = "SUBSTITUTED".equals(it.getFulfillStatus());
            BigDecimal price = sub && it.getFulfilledUnitPrice() != null
                    ? it.getFulfilledUnitPrice() : it.getUnitPrice();
            BigDecimal line = price.multiply(BigDecimal.valueOf(it.getQuantity()));
            drugTotal = drugTotal.add(line);
            String catalog = sub && it.getFulfilledCatalog() != null
                    ? it.getFulfilledCatalog() : it.getDrug().getInsuranceCatalog();
            if (!"否".equals(catalog)) eligible = eligible.add(line);
        }
        BigDecimal pooling = eligible.multiply(r.poolingRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal chronic = BigDecimal.ZERO;
        if (o.isChronicFlag()) {
            chronic = eligible.subtract(pooling).min(r.chronicQuotaLeft()).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal nf = nightFee(drugTotal);
        BigDecimal selfPay = drugTotal.subtract(pooling).subtract(chronic).add(nf)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        o.setDrugTotal(drugTotal);
        o.setPoolingPay(pooling);
        o.setChronicPay(chronic);
        o.setNightFee(nf);
        o.setSelfPay(selfPay);
        o.setTotalAmount(selfPay);
    }

    private BigDecimal sumDrugTotal(DispenseOrder o) {
        BigDecimal t = BigDecimal.ZERO;
        for (OrderItem it : items.findByOrderOrderByIdAsc(o)) {
            if ("REMOVED".equals(it.getFulfillStatus())) continue;
            BigDecimal price = "SUBSTITUTED".equals(it.getFulfillStatus()) && it.getFulfilledUnitPrice() != null
                    ? it.getFulfilledUnitPrice() : it.getUnitPrice();
            t = t.add(price.multiply(BigDecimal.valueOf(it.getQuantity())));
        }
        return t;
    }

    private BigDecimal nightFee(BigDecimal drugTotal) {
        return drugTotal.multiply(nightFeeRate).max(nightFeeMin).setScale(2, RoundingMode.HALF_UP);
    }

    // ============================================================
    // 收银：支付与发票
    // ============================================================
    @Transactional
    public DispenseOrder pay(User cashier, Long orderId, PayReq req) {
        DispenseOrder o = mustGet(orderId);
        if (!"WAIT_PAYMENT".equals(o.getStatus()))
            throw new ApiException("当前状态(" + o.getStatus() + ")不能支付");
        if (o.getTotalAmount() == null || o.getSelfPay() == null)
            throw new ApiException("费用尚未核算");
        o.setPaymentMethod(req.method() == null ? "微信" : req.method());
        o.setInvoiceTitle(req.invoiceTitle());
        o.setInvoiceTaxNo(req.invoiceTaxNo());
        o.setPaidAt(LocalDateTime.now());
        o.setStatus("PAID");
        o.setDecision("CONTINUE");
        recordEvent(o, "PAY", "CONTINUE",
                "收银确认到账 " + money(o.getTotalAmount()) + " 元（" + o.getPaymentMethod()
                        + "）；发票抬头：" + nz(req.invoiceTitle(), "个人")
                        + (req.invoiceTaxNo() != null ? "，税号 " + req.invoiceTaxNo() : "")
                        + "。付款明细——自费 " + money(o.getSelfPay()) + "、统筹 " + money(o.getPoolingPay())
                        + "、慢病额度 " + money(o.getChronicPay()) + "、夜间服务费 " + money(o.getNightFee())
                        + "。继续出库", cashier, "WAIT_PAYMENT", "PAID");
        return o;
    }

    // ============================================================
    // 仓管：缺药 / 临期 / 替代药 / 出库
    // ============================================================
    @Transactional
    public DispenseOrder substitute(User warehouse, Long orderId, SubstituteReq req) {
        DispenseOrder o = mustGet(orderId);
        if (!List.of("PAID", "PICKING", "PAUSED").contains(o.getStatus()))
            throw new ApiException("仅支付后出库环节可处理缺药替代");
        OrderItem it = items.findById(req.itemId())
                .orElseThrow(() -> new ApiException("明细不存在"));
        if (!it.getOrder().getId().equals(o.getId())) throw new ApiException("明细不属于该配药单");
        Drug alt = drugs.findById(req.replacementDrugId())
                .orElseThrow(() -> new ApiException("替代药品不存在"));
        int qty = req.quantity() != null ? req.quantity() : it.getQuantity();
        if (alt.getStock() < qty) throw new ApiException("替代药《" + alt.getName() + "》库存不足");
        it.setFulfillStatus("SUBSTITUTED");
        it.setFulfilledDrugName(alt.getName());
        it.setFulfilledUnitPrice(alt.getPrice());
        it.setFulfilledCatalog(alt.getInsuranceCatalog());
        it.setQuantity(qty);
        it.setReviewNote((it.getReviewNote() == null ? "" : it.getReviewNote() + "；")
                + "仓管替代：" + it.getDrug().getName() + " → " + alt.getName()
                + "（批号" + alt.getBatchNo() + "/效期" + alt.getExpiryDate() + "）：" + nz(req.note(), ""));
        if (alt.isColdChain()) o.setColdChainRequired(true);
        // 重算费用（替代药价格/目录可能不同）
        if (o.getInsuranceStatus().equals("APPROVED")) {
            var r = insurance.verify(o.getInsuranceNo(), o.isChronicFlag(), chronicUsedDefault, chronicQuota);
            calculateAmounts(o, r);
        } else {
            o.setDrugTotal(sumDrugTotal(o));
            o.setNightFee(nightFee(o.getDrugTotal()));
            o.setSelfPay(o.getDrugTotal().add(o.getNightFee()));
            o.setTotalAmount(o.getSelfPay());
        }
        recordEvent(o, "SUBSTITUTE", "CONTINUE",
                "原药品《" + it.getDrug().getName() + "》缺药/临期，仓改配发替代药《" + alt.getName()
                        + "》×" + qty + "（批号 " + alt.getBatchNo() + "，效期 " + alt.getExpiryDate()
                        + "，单价 " + money(alt.getPrice()) + "）。费用已重新拆分，应付 "
                        + money(o.getTotalAmount()) + " 元。" + nz(req.note(), ""),
                warehouse, null, o.getStatus());
        return o;
    }

    /** 缺药且无替代：移除条目并据实重算，说明继续/暂停理由。 */
    @Transactional
    public DispenseOrder removeItem(User warehouse, Long orderId, Long itemId, String reason) {
        DispenseOrder o = mustGet(orderId);
        OrderItem it = items.findById(itemId).orElseThrow(() -> new ApiException("明细不存在"));
        if (!it.getOrder().getId().equals(o.getId())) throw new ApiException("明细不属于该配药单");
        String name = it.getDrug().getName();
        it.setFulfillStatus("REMOVED");
        it.setReviewNote("缺药且无可接受替代，删除该药品：" + reason);
        if (o.getInsuranceStatus().equals("APPROVED")) {
            var r = insurance.verify(o.getInsuranceNo(), o.isChronicFlag(), chronicUsedDefault, chronicQuota);
            calculateAmounts(o, r);
        } else {
            o.setDrugTotal(sumDrugTotal(o));
            o.setNightFee(nightFee(o.getDrugTotal()));
            o.setSelfPay(o.getDrugTotal().add(o.getNightFee()));
            o.setTotalAmount(o.getSelfPay());
        }
        long remain = items.findByOrderOrderByIdAsc(o).stream()
                .filter(x -> !"REMOVED".equals(x.getFulfillStatus())).count();
        if (remain == 0) {
            pause(o, "全单药品均缺药且无替代", warehouse);
            recordEvent(o, "PAUSE", "PAUSE",
                    "全单药品均缺药且无可用替代，暂停配药，建议患者线下复诊或改日到有药机构购买",
                    warehouse, null, "PAUSED");
        } else {
            recordEvent(o, "SUBSTITUTE", "CONTINUE",
                    "《" + name + "》缺药且无替代，经患者同意从本单剔除，按剩余药品继续配药；费用已重算为 "
                            + money(o.getTotalAmount()) + " 元。原因：" + reason, warehouse, null, o.getStatus());
        }
        return o;
    }

    /** 患者要求改配送地址（出库前/配送前均可，全员在同一单据可见）。 */
    @Transactional
    public DispenseOrder changeAddress(User actor, Long orderId, AddressReq req) {
        DispenseOrder o = mustGet(orderId);
        if (List.of("COMPLETED", "CANCELLED", "OFFLINE_REFERRAL").contains(o.getStatus()))
            throw new ApiException("已终结单据不能修改地址");
        String old = nz(o.getAddress(), "") + "/" + nz(o.getRecipient(), "") + "/" + nz(o.getContactPhone(), "");
        o.setAddress(req.address());
        o.setRecipient(req.recipient());
        o.setContactPhone(req.contactPhone());
        recordEvent(o, "ADDRESS_CHANGE", "CONTINUE",
                "配送信息变更：[" + old + "] → [" + req.address() + "/" + req.recipient() + "/"
                        + req.contactPhone() + "]。仓管、骑手按新地址协同，继续配药。原因："
                        + nz(req.note(), "患者要求"), actor, null, o.getStatus());
        return o;
    }

    /** 骑手保冷能力确认；无法保冷则暂停，等待改自取/换骑手。 */
    @Transactional
    public DispenseOrder coldConfirm(User rider, Long orderId, ColdConfirmReq req) {
        DispenseOrder o = mustGet(orderId);
        o.setRider(rider);
        o.setRiderColdCapable(req.capable());
        if (req.capable()) {
            if ("PAUSED".equals(o.getStatus())) {
                resume(rider, o, "骑手已配备冷链箱(2-8℃)，恢复配药", "COLD_CHAIN");
            } else {
                recordEvent(o, "COLD_CHAIN", "CONTINUE",
                        "骑手 " + rider.getDisplayName() + " 确认已配备冷链保温箱+冰排，可全程 2-8℃ 保冷："
                                + nz(req.note(), ""), rider, null, o.getStatus());
            }
        } else {
            pause(o, "骑手反馈无法全程保冷（" + nz(req.note(), "冷链设备不足")
                    + "），冷链药品暂停出库/配送，等待改自取、换冷链骑手或药师评估", rider);
            recordEvent(o, "COLD_CHAIN", "PAUSE",
                    "骑手无法保冷，单据已暂停：冷链药品断链风险不可接受，需患者/药师/仓管协同改方案",
                    rider, null, o.getStatus());
        }
        return o;
    }

    /** 出库：批号/效期冻结、扣减库存、生成批号快照。 */
    @Transactional
    public DispenseOrder outbound(User warehouse, Long orderId) {
        DispenseOrder o = mustGet(orderId);
        if (!"PAID".equals(o.getStatus())) throw new ApiException("仅已支付待出库状态可出库");
        if (o.isColdChainRequired() && o.isNeedsDelivery()
                && o.getRiderColdCapable() != null && !o.getRiderColdCapable()) {
            throw new ApiException("骑手无法保冷，冷链药品不能出库");
        }
        List<OrderItem> its = items.findByOrderOrderByIdAsc(o);
        List<String> snapshot = new ArrayList<>();
        for (OrderItem it : its) {
            if ("REMOVED".equals(it.getFulfillStatus())) continue;
            Drug pick;
            if ("SUBSTITUTED".equals(it.getFulfillStatus())) {
                pick = drugs.findByNameContaining(it.getFulfilledDrugName()).stream()
                        .filter(d -> d.getName().equals(it.getFulfilledDrugName())).findFirst()
                        .orElse(it.getDrug());
            } else {
                pick = it.getDrug();
            }
            if (pick.getStock() < it.getQuantity())
                throw new ApiException("《" + pick.getName() + "》实际库存不足，无法出库，请走缺药替代流程");
            pick.setStock(pick.getStock() - it.getQuantity());
            drugs.save(pick);
            it.setOutBatchNo(pick.getBatchNo());
            it.setOutExpiryDate(pick.getExpiryDate());
            if (pick.getExpiryDate() != null && !pick.getExpiryDate().isAfter(LocalDate.now().plusDays(90))) {
                it.setNearExpiryFlagDate(pick.getExpiryDate());
            }
            snapshot.add(pick.getName() + "×" + it.getQuantity() + "(批号" + pick.getBatchNo()
                    + "/效期" + pick.getExpiryDate() + ")");
        }
        o.setOutAt(LocalDateTime.now());
        o.setOutBy(warehouse);
        o.setOutBatchSnapshot(String.join("；", snapshot));
        o.setStatus(o.isNeedsDelivery() ? "DELIVERING" : "PICKING");
        o.setDecision("CONTINUE");
        String coldNote = o.isColdChainRequired()
                ? (o.isNeedsDelivery() ? "；冷链药品待骑手温控配送" : "；冷链药品已交接患者冷藏")
                : "";
        String nearNote = its.stream().anyMatch(x -> x.getNearExpiryFlagDate() != null)
                ? "；含临期批号已在档案中标注并告知患者" : "";
        recordEvent(o, "ISSUE", "CONTINUE",
                "仓管拣选复核出库，批号/效期快照：" + String.join("；", snapshot)
                        + coldNote + nearNote + "。" + (o.isNeedsDelivery() ? "转骑手配送" : "待患者到店自取签收"),
                warehouse, "PAID", o.getStatus());
        return o;
    }

    /** 签收归档：温控照片、温度、签收人、用药提醒入档案。 */
    @Transactional
    public DispenseOrder deliver(User actor, Long orderId, DeliverReq req) {
        DispenseOrder o = mustGet(orderId);
        if (!List.of("DELIVERING", "PICKING").contains(o.getStatus()))
            throw new ApiException("当前状态不能签收");
        boolean riderDelivery = "DELIVERING".equals(o.getStatus());
        if (riderDelivery) {
            if (o.isColdChainRequired()) {
                if (req.temperature() == null || req.coldPhotoPath() == null)
                    throw new ApiException("冷链配送必须填写温控温度并上传温控照片");
                if (req.temperature().compareTo(new BigDecimal("8")) > 0
                        || req.temperature().compareTo(new BigDecimal("2")) < 0) {
                    pause(o, "到件温控温度 " + req.temperature() + "℃ 超出 2-8℃ 区间，暂停签收等待药师/客服处置",
                            actor);
                    recordEvent(o, "COLD_CHAIN", "PAUSE",
                            "配送温控记录异常（" + req.temperature() + "℃），药品可能脱冷，暂停签收",
                            actor, null, "PAUSED");
                    return o;
                }
            }
            o.setRider(actor);
        }
        o.setColdPhotoPath(req.coldPhotoPath());
        o.setColdTemperature(req.temperature());
        o.setSignedBy(req.signedBy());
        o.setSignRelation(req.signRelation());
        o.setMedicationReminder(req.medicationReminder());
        o.setDeliveredAt(LocalDateTime.now());
        o.setStatus("COMPLETED");
        o.setDecision("CONTINUE");
        StringJoiner sj = new StringJoiner("。");
        sj.add((riderDelivery ? "骑手配送到达，签收人：" + req.signedBy()
                + "（" + nz(req.signRelation(), "本人") + "）" : "患者到店自取签收：" + req.signedBy()));
        if (o.isColdChainRequired() && req.temperature() != null)
            sj.add("配送温控 " + req.temperature() + "℃，温控照片已归档");
        if (req.medicationReminder() != null && !req.medicationReminder().isBlank())
            sj.add("用药提醒：" + req.medicationReminder());
        sj.add("批号、审方意见、付款明细、签收信息全部进入配药档案，流程完成");
        recordEvent(o, "DELIVER", "CONTINUE", sj.toString(), actor,
                riderDelivery ? "DELIVERING" : "PICKING", "COMPLETED");
        return o;
    }

    // ============================================================
    // 通用：暂停 / 恢复 / 转线下
    // ============================================================
    @Transactional
    public DispenseOrder pauseOrder(User actor, Long orderId, String reason) {
        DispenseOrder o = mustGet(orderId);
        pause(o, reason, actor);
        recordEvent(o, "PAUSE", "PAUSE", "人工暂停配药：" + reason, actor, null, "PAUSED");
        return o;
    }

    @Transactional
    public DispenseOrder resumeOrder(User actor, Long orderId, String reason) {
        DispenseOrder o = mustGet(orderId);
        resume(actor, o, reason, "RESUME");
        return o;
    }

    @Transactional
    public DispenseOrder offlineReferral(User actor, Long orderId, String reason) {
        DispenseOrder o = mustGet(orderId);
        offline(o, reason, actor);
        recordEvent(o, "OFFLINE", "OFFLINE", "终止线上配药，改为线下复诊：" + reason,
                actor, null, "OFFLINE_REFERRAL");
        return o;
    }

    private void pause(DispenseOrder o, String reason, User actor) {
        if (!"PAUSED".equals(o.getStatus())) o.setPrevStatus(o.getStatus());
        o.setStatus("PAUSED");
        o.setDecision("PAUSE");
    }

    private void resume(User actor, DispenseOrder o, String reason, String eventType) {
        String to = o.getPrevStatus() != null ? o.getPrevStatus() : "SUBMITTED";
        o.setStatus(to);
        o.setDecision("CONTINUE");
        o.setPrevStatus(null);
        recordEvent(o, eventType, "CONTINUE", "恢复配药，回到「" + to + "」环节：" + reason,
                actor, "PAUSED", to);
    }

    private void offline(DispenseOrder o, String reason, User actor) {
        o.setStatus("OFFLINE_REFERRAL");
        o.setDecision("OFFLINE");
    }

    // ============================================================
    // 夜间值班药师交接
    // ============================================================
    @Transactional
    public Map<String, Object> handover(User fromPharmacist, HandoverReq req) {
        User to = users.findById(req.toPharmacistId())
                .orElseThrow(() -> new ApiException("接班药师不存在"));
        if (!"PHARMACIST".equals(to.getRole())) throw new ApiException("接班人必须是药师");
        if (to.getId().equals(fromPharmacist.getId())) throw new ApiException("不能交接给自己");
        boolean alreadyOnDuty = shifts.findByOnDutyTrue().stream()
                .anyMatch(s -> s.getPharmacist().getId().equals(to.getId()));
        if (alreadyOnDuty) throw new ApiException("接班药师已在岗，无需重复交接");
        // 结束交班人在岗班次，建立接班人班次
        List<PharmacistShift> onDuty = shifts.findByOnDutyTrue();
        for (PharmacistShift s : onDuty) {
            if (s.getPharmacist().getId().equals(fromPharmacist.getId())) {
                s.setOnDuty(false);
                s.setEndedAt(LocalDateTime.now());
                s.setHandedTo(to);
                s.setHandoverSummary(req.summary());
                s.setHandedOverAt(LocalDateTime.now());
            }
        }
        PharmacistShift ns = new PharmacistShift();
        ns.setPharmacist(to);
        ns.setShiftDate(LocalDate.now().toString());
        ns.setShiftType("NIGHT");
        ns.setOnDuty(true);
        shifts.save(ns);

        List<Long> targetIds = req.orderIds() != null && !req.orderIds().isEmpty()
                ? req.orderIds() : null;
        int moved = 0;
        for (DispenseOrder o : orders.findAll()) {
            if (!HANDOVER_STATUSES.contains(o.getStatus())) continue;
            if (o.getPharmacist() != null && !o.getPharmacist().getId().equals(fromPharmacist.getId())
                    && targetIds == null) continue;
            if (targetIds != null && !targetIds.contains(o.getId())) continue;
            o.setHandedFrom(fromPharmacist);
            o.setHandedTo(to);
            o.setHandoverNote(req.summary());
            o.setHandedOverAt(LocalDateTime.now());
            o.setPharmacist(to);
            moved++;
            recordEvent(o, "HANDOVER", "CONTINUE",
                    "夜间值班药师交接：" + fromPharmacist.getDisplayName() + " → " + to.getDisplayName()
                            + "。在途单据及注意事项同步：" + nz(req.summary(), "无")
                            + "。接班人继续负责该配药单", fromPharmacist, null, o.getStatus());
        }
        return Map.of("movedOrders", moved, "toPharmacist", to.getDisplayName());
    }

    // ============================================================
    // 次日投诉与同一档案追溯
    // ============================================================
    @Transactional
    public Complaint fileComplaint(User reporter, Long orderId, ComplaintReq req) {
        DispenseOrder o = mustGet(orderId);
        Complaint c = new Complaint();
        c.setOrder(o);
        c.setReporter(reporter);
        c.setCategory(req.category());
        c.setContent(req.content());
        c.setStatus("OPEN");
        c.setTracedStage(switch (nz(req.category(), "")) {
            case "MISSING_DRUG" -> "OUTBOUND";
            case "INSURANCE_DISPUTE" -> "PAYMENT";
            case "ADVERSE_REACTION" -> "REVIEW";
            default -> "DELIVERY";
        });
        complaints.save(c);
        String stageText = switch (c.getTracedStage()) {
            case "OUTBOUND" -> "出库/批号环节";
            case "PAYMENT" -> "医保与支付环节";
            case "REVIEW" -> "审方与用药指导环节";
            default -> "配送签收环节";
        };
        recordEvent(o, "COMPLAINT", "INFO",
                "收到患者次日反馈（" + categoryText(req.category()) + "）：" + req.content()
                        + "。客服已在同一配药档案立案，初步追溯定位：" + stageText
                        + "，可调取审方意见、出库批号快照、付款明细与签收温控记录核查",
                reporter, null, o.getStatus());
        return c;
    }

    @Transactional
    public Complaint handleComplaint(User cs, Long complaintId, HandleComplaintReq req) {
        Complaint c = complaints.findById(complaintId)
                .orElseThrow(() -> new ApiException("投诉单不存在"));
        c.setStatus(req.status());
        c.setHandlingNote(req.note());
        c.setHandler(cs);
        c.setHandledAt(LocalDateTime.now());
        String result = switch (req.status()) {
            case "RESOLVED" -> "已解决";
            case "REJECTED" -> "核查后不成立";
            default -> "处理中";
        };
        recordEvent(c.getOrder(), "COMPLAINT",
                "RESOLVED".equals(req.status()) ? "CONTINUE" : "INFO",
                "客服依据同一配药记录完成追溯核查（审方→出库→支付→签收），投诉结论：" + result
                        + "。处理说明：" + req.note(), cs, null, c.getOrder().getStatus());
        return c;
    }

    // ============================================================
    // 查询辅助
    // ============================================================
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long orderId) {
        DispenseOrder o = mustGet(orderId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", o);
        m.put("items", items.findByOrderOrderByIdAsc(o));
        m.put("events", events.findByOrderOrderByCreatedAtAscIdAsc(o));
        m.put("complaints", complaints.findAll().stream()
                .filter(c -> c.getOrder().getId().equals(o.getId())).toList());
        return m;
    }

    @Transactional(readOnly = true)
    public List<OrderEvent> timeline(Long orderId) {
        return events.findByOrderOrderByCreatedAtAscIdAsc(mustGet(orderId));
    }

    private DispenseOrder mustGet(Long id) {
        return orders.findById(id).orElseThrow(() -> new ApiException(404, "配药单不存在"));
    }

    private void requireOnDutyPharmacist(User p) {
        boolean on = shifts.findByOnDutyTrue().stream()
                .anyMatch(s -> s.getPharmacist().getId().equals(p.getId()));
        if (!on) throw new ApiException(403, "您当前不在夜间值班班次内，请先接班或由值班药师操作");
    }

    private void recordEvent(DispenseOrder o, String type, String decision, String reason,
                             User actor, String from, String to) {
        OrderEvent e = new OrderEvent(o, type, decision, reason, actor);
        e.setFromStatus(from);
        e.setToStatus(to);
        orders.save(o);
        events.save(e);
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s, String def) { return s == null || s.isBlank() ? def : s; }

    private static String categoryText(String c) {
        return switch (nz(c, "")) {
            case "ADVERSE_REACTION" -> "疑似不良反应";
            case "MISSING_DRUG" -> "漏发药品";
            case "INSURANCE_DISPUTE" -> "医保扣费争议";
            default -> "其他问题";
        };
    }
}
