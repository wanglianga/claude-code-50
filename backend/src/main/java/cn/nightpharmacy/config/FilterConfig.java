package cn.nightpharmacy.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<SpaForwardFilter> spaFilter() {
        FilterRegistrationBean<SpaForwardFilter> bean = new FilterRegistrationBean<>(new SpaForwardFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return bean;
    }
}
