package cn.nightpharmacy.config;

import cn.nightpharmacy.domain.*;
import cn.nightpharmacy.repo.*;
import cn.nightpharmacy.service.OrderService;
import cn.nightpharmacy.service.Requests.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 演示数据：六类角色账号、药品批号库存、夜间值班药师，
 * 以及两张不同环节的演示配药单（已完成归档+投诉 / 医保接口异常暂停）。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository users;
    private final DrugRepository drugs;
    private final ShiftRepository shifts;
    private final OrderService orderService;

    @Value("${app.storage-dir}")
    private String storageDir;

    public DataInitializer(UserRepository users, DrugRepository drugs, ShiftRepository shifts,
                           OrderService orderService) {
        this.users = users;
        this.drugs = drugs;
        this.shifts = shifts;
        this.orderService = orderService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureDemoImages();
        if (users.count() > 0) return;

        // ---------------- 账号（密码统一 123456，admin 为 admin123） ----------------
        User patient = u("zhangwei", "张伟（患者家属）", "PATIENT", "13800001111", null);
        User patient2 = u("lifang", "李芳（糖尿病患者）", "PATIENT", "13800002222", null);
        User pharm = u("wangyaoshi", "王静（值班药师）", "PHARMACIST", "13900001001", "P-2021-0321");
        u("zhouyaoshi", "周敏（接班药师）", "PHARMACIST", "13900001002", "P-2019-0188");
        u("chenyin", "陈丽（夜班收银）", "CASHIER", "13700003001", null);
        u("laocang", "赵德柱（仓管）", "WAREHOUSE", "13700003002", null);
        u("xiaoma", "马奔（冷链骑手）", "RIDER", "13600004001", null);
        u("kefu_an", "安心（客服）", "CUSTOMER_SERVICE", "13500005001", null);
        u("admin", "系统管理员", "ADMIN", "13000000000", null);
        User admin = users.findByUsername("admin").orElseThrow();
        admin.setPassword("admin123");
        users.save(admin);

        // ---------------- 药品目录 / 库存批号 / 效期 ----------------
        drug("阿莫西林胶囊", "0.25g*24粒", "华北制药", "12.80", "RX", false, 86,
                "HB240611", LocalDate.of(2027, 6, 30), "甲",
                "头孢克洛干混悬剂", "青霉素过敏者禁用");
        drug("布洛芬混悬液", "100ml:2g", "美林", "28.50", "OTC", false, 40,
                "ML250302", LocalDate.of(2027, 3, 15), "乙",
                "对乙酰氨基酚混悬液", "消化道溃疡者慎用");
        Drug insulin = drug("门冬胰岛素30注射液", "300IU*3ml*5支", "诺和诺德", "268.00", "RX", true, 18,
                "NN250118", LocalDate.of(2027, 1, 20), "乙",
                "精蛋白生物合成人胰岛素注射液", "未按时进餐者防低血糖；须2-8℃冷藏");
        drug("硝苯地平控释片", "30mg*7片", "拜耳", "32.00", "RX", false, 120,
                "BA241209", LocalDate.of(2028, 12, 1), "甲",
                "苯磺酸氨氯地平片", "低血压禁用，勿掰开服用");
        drug("阿奇霉素片", "0.25g*6片", "希舒美", "45.00", "RX", false, 55,
                "PF240819", LocalDate.of(2026, 11, 10), "甲",
                "罗红霉素胶囊", "肝功能不全慎用；注意 QT 间期");
        drug("蒙脱石散", "3g*10袋", "思密达", "22.00", "OTC", false, 70,
                "BO250211", LocalDate.of(2027, 8, 1), "乙",
                "口服补液盐Ⅲ", "与其他药物间隔1-2小时");
        drug("氯雷他定片", "10mg*6片", "开瑞坦", "19.80", "OTC", false, 64,
                "BA241125", LocalDate.of(2027, 11, 1), "乙",
                "西替利嗪片", "严重肝功能不全减量");
        drug("艾司唑仑片", "1mg*20片", "华中药业", "15.60", "CONTROLLED", false, 9,
                "HZ240507", LocalDate.of(2027, 5, 1), "否",
                null, "第二类精神药品，夜间限购2盒，须急诊医生电话核实");
        drug("奥司他韦颗粒", "15mg*10袋", "东阳光", "68.00", "RX", false, 0,
                "DY240316", LocalDate.of(2026, 10, 25), "甲",
                "玛巴洛沙韦片", "肾功能不全需调整剂量");
        drug("玛巴洛沙韦片", "40mg*1片", "罗氏", "228.00", "RX", false, 12,
                "RH250408", LocalDate.of(2027, 4, 1), "乙",
                "奥司他韦颗粒", "单剂量顿服，避免与乳制品同服");
        drug("口服补液盐Ⅲ", "5.125g*6袋", "西安安健", "14.50", "OTC", false, 90,
                "AJ250108", LocalDate.of(2027, 1, 30), "甲",
                null, "心肾功能不全者监测电解质");
        drug("头孢克肟胶囊", "100mg*12粒", "广药", "36.00", "RX", false, 48,
                "GY240901", LocalDate.of(2027, 9, 1), "甲",
                "阿莫西林克拉维酸钾片", "头孢/青霉素过敏者禁用");
        Drug rehydration = drugs.findByNameContaining("口服补液盐Ⅲ").stream()
                .filter(d -> d.getSpec().contains("5.125g")).findFirst().orElse(null);

        // ---------------- 夜间值班：王静在岗，周敏未上岗（用于交接演示） ----------------
        PharmacistShift shift = new PharmacistShift();
        shift.setPharmacist(pharm);
        shift.setShiftDate(LocalDate.now().toString());
        shift.setShiftType("NIGHT");
        shift.setOnDuty(true);
        shifts.save(shift);

        // ---------------- 演示单 1：已完成归档 + 次日不良反应投诉（全链路追溯） ----------------
        User cashier = users.findByUsername("chenyin").orElseThrow();
        User warehouse = users.findByUsername("laocang").orElseThrow();
        User rider = users.findByUsername("xiaoma").orElseThrow();
        User cs = users.findByUsername("kefu_an").orElseThrow();

        var submit1 = new SubmitOrder(
                "深夜高热 39.2℃伴呕吐，糖尿病史，需退热并补充胰岛素",
                "青霉素过敏",
                "二甲双胍片 每日2次",
                "/files/demo/prescription-1.jpg",
                "陈建华（急诊内科）", "市第一人民医院急诊科",
                LocalDate.now(), "320102199003072216", true, true,
                "朝阳路88号桂花苑3栋502", "13800002222", "李芳（本人）",
                null,
                List.of(new ItemReq(insulin.getId(), 1, "每次12单位 早晚餐前皮下注射"),
                        new ItemReq(drugs.findByNameContaining("布洛芬混悬液").get(0).getId(), 1,
                                "发热时服10ml 间隔6小时")));
        DispenseOrder o1 = orderService.submit(patient2, submit1);
        orderService.review(pharm, o1.getId(), new ReviewReq("已核对剂量，嘱低血糖时立即口服糖水", false));
        orderService.pay(cashier, o1.getId(), new PayReq("医保电子凭证", "李芳", null));
        orderService.coldConfirm(rider, o1.getId(),
                new ColdConfirmReq(true, "冷链箱2-8℃，冰排2块"));
        orderService.outbound(warehouse, o1.getId());
        orderService.deliver(rider, o1.getId(), new DeliverReq(
                "/files/demo/cold-chain-1.jpg", new BigDecimal("4.60"),
                "李芳", "本人", "胰岛素2-8℃冷藏勿冷冻；注射后按时进餐，出现心慌出汗立即补糖"));
        orderService.fileComplaint(patient2, o1.getId(),
                new ComplaintReq("ADVERSE_REACTION",
                        "用药后次日凌晨出现轻微恶心，怀疑布洛芬胃肠道反应，请求药师复核并给出处理建议"));

        // ---------------- 演示单 2：医保接口超时异常，单据暂停（重试/回退分支） ----------------
        var submit2 = new SubmitOrder(
                "腹泻伴脱水 4 小时，夜间急诊后持方购药",
                "无",
                "无",
                "/files/demo/prescription-2.jpg",
                "刘洋（急诊内科）", "市第二人民医院",
                LocalDate.now(), "3201051988080866ERR", false, false,
                null, "13800001111", "张伟（家属）",
                null,
                List.of(new ItemReq(drugs.findByNameContaining("蒙脱石散").get(0).getId(), 2,
                                "每次1袋 每日3次"),
                        new ItemReq(rehydration.getId(), 1, "每次1袋 温水冲服 每日4次")));
        DispenseOrder o2 = orderService.submit(patient, submit2);
        orderService.review(pharm, o2.getId(), new ReviewReq("剂量疗程合理", false));
    }

    private User u(String username, String name, String role, String phone, String license) {
        User user = new User(username, "123456", name, role, phone);
        user.setLicenseNo(license);
        return users.save(user);
    }

    private Drug drug(String name, String spec, String maker, String price, String cat,
                      boolean cold, int stock, String batch, LocalDate expiry,
                      String catalog, String alt, String contra) {
        Drug d = new Drug();
        d.setName(name);
        d.setSpec(spec);
        d.setManufacturer(maker);
        d.setPrice(new BigDecimal(price));
        d.setControlCategory(cat);
        d.setColdChain(cold);
        d.setStock(stock);
        d.setBatchNo(batch);
        d.setExpiryDate(expiry);
        d.setInsuranceCatalog(catalog);
        d.setAlternative(alt);
        d.setContraindication(contra);
        return drugs.save(d);
    }

    /** 生成两张演示占位图（电子处方、温控照片），避免档案中图片 404。 */
    private void ensureDemoImages() {
        try {
            writeDemoImage("/demo/prescription-1.jpg", "Rx 急诊电子处方",
                    new String[]{"市第一人民医院 急诊科", "患者：李芳  诊断：急性上呼吸道感染/2型糖尿病",
                            "Rp：门冬胰岛素30注射液 ×1盒；布洛芬混悬液 ×1瓶",
                            "医师签名：陈建华    2026-09-12"}, new Color(235, 245, 255));
            writeDemoImage("/demo/prescription-2.jpg", "Rx 急诊电子处方",
                    new String[]{"市第二人民医院 急诊科", "患者：张伟（家属代述）  诊断：急性胃肠炎伴脱水",
                            "Rp：蒙脱石散 ×2盒；口服补液盐Ⅲ ×1盒",
                            "医师签名：刘洋    2026-09-12"}, new Color(235, 250, 240));
            writeDemoImage("/demo/cold-chain-1.jpg", "冷链配送温控记录",
                    new String[]{"药品：门冬胰岛素30注射液", "全程温度：2-8℃    签收温度：4.6℃",
                            "冷链箱编号：CL-007  冰排×2", "骑手：马奔    签收人：李芳（本人）"},
                    new Color(230, 245, 255));
        } catch (Exception ignored) {
        }
    }

    private void writeDemoImage(String relative, String title, String[] lines, Color bg) throws Exception {
        File f = new File(storageDir + relative);
        f.getParentFile().mkdirs();
        if (f.exists()) return;
        BufferedImage img = new BufferedImage(800, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, 800, 480);
        g.setColor(new Color(40, 60, 90));
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString(title, 40, 60);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        int y = 120;
        for (String line : lines) { g.drawString(line, 40, y); y += 55; }
        g.drawRect(20, 20, 760, 440);
        g.dispose();
        ImageIO.write(img, "jpg", f);
    }
}
