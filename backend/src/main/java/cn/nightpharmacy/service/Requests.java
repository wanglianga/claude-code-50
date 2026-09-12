package cn.nightpharmacy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 接口层请求 DTO 集合。 */
public final class Requests {
    private Requests() {}

    public record ItemReq(Long drugId, Integer quantity, String dosage) {}

    public record SubmitOrder(
            String symptoms, String allergies, String currentMedication,
            String prescriptionImagePath,
            String prescribingDoctor, String doctorHospital,
            LocalDate prescriptionDate, String insuranceNo,
            boolean chronicFlag, boolean needsDelivery,
            String address, String contactPhone, String recipient,
            String supplement,
            List<ItemReq> items
    ) {}

    public record ReviewReq(String opinion, boolean riskAcknowledged) {}

    public record DoctorVerifyReq(String doctorName, String phone, String note) {}

    public record PayReq(String method, String invoiceTitle, String invoiceTaxNo) {}

    public record SubstituteReq(Long itemId, Long replacementDrugId, Integer quantity, String note) {}

    public record ColdConfirmReq(boolean capable, String note) {}

    public record DeliverReq(String coldPhotoPath, BigDecimal temperature,
                             String signedBy, String signRelation, String medicationReminder) {}

    public record ComplaintReq(String category, String content) {}

    public record HandleComplaintReq(String status, String note) {}

    public record HandoverReq(Long toPharmacistId, String summary, List<Long> orderIds) {}

    public record DecisionReq(String reason) {}

    public record AddressReq(String address, String contactPhone, String recipient, String note) {}
}
