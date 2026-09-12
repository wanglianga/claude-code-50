package cn.nightpharmacy.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 让 Jackson 正确序列化 Hibernate6 的懒加载代理（ManyToOne 关联的 User 等），
 * 未初始化的关联输出为 null，不强制发 SQL。
 */
@Configuration
public class JacksonConfig {
    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // OSIV 已开启：序列化时正常初始化 ManyToOne 代理，前端可直接取 patient.displayName 等
        module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        return module;
    }
}
