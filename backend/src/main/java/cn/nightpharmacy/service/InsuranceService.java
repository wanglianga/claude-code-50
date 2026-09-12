package cn.nightpharmacy.service;

import cn.nightpharmacy.web.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 医保接口模拟器。
 * 真实环境对接医保局实时接口；此处以凭证号规则模拟：
 *  - 凭证号以 "ERR" 结尾        -> 接口返回异常（超时/报文错误），允许重试或医保回退
 *  - 凭证号以 "000" 结尾        -> 核验不通过（断保/黑名单）
 *  - 其余有效 12~25 位凭证号     -> 核验通过
 *
 *  返回：统筹支付比例、门诊慢病可用额度。
 */
@Service
public class InsuranceService {

    public record InsuranceResult(boolean approved, String message,
                                  BigDecimal poolingRate, BigDecimal chronicQuotaLeft,
                                  boolean interfaceError) {}

    public InsuranceResult verify(String insuranceNo, boolean chronic, BigDecimal chronicAlreadyUsed,
                                  BigDecimal annualQuota) {
        if (insuranceNo == null || insuranceNo.isBlank()) {
            return new InsuranceResult(false, "未提供医保凭证，按全自费处理",
                    BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        String no = insuranceNo.trim();
        if (no.endsWith("ERR")) {
            // 模拟夜间医保接口抖动
            return new InsuranceResult(false, "医保接口返回异常：网关超时(HTTP 504)，请稍后重试或执行医保回退",
                    BigDecimal.ZERO, BigDecimal.ZERO, true);
        }
        if (no.endsWith("000")) {
            return new InsuranceResult(false, "医保核验不通过：参保状态异常（暂停参保），需全自费或医保回退",
                    BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        if (no.length() < 8) {
            return new InsuranceResult(false, "医保凭证号格式不正确", BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        BigDecimal poolingRate = new BigDecimal("0.60"); // 夜间急诊统筹报销 60%（目录内费用）
        BigDecimal chronicLeft = chronic
                ? annualQuota.subtract(chronicAlreadyUsed).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        String msg = chronic
                ? "医保核验通过；急诊统筹按 60% 报销，门诊慢病年度剩余额度 " + chronicLeft + " 元"
                : "医保核验通过；急诊统筹按 60% 报销";
        return new InsuranceResult(true, msg, poolingRate, chronicLeft, false);
    }
}
