package cn.nightpharmacy.config;

/**
 * SPA history 路由回退：非静态资源请求转发到 /index.html。
 * 仅通过 FilterConfig 注册一次（不使用 @Component，避免 Spring Boot 与
 * FilterRegistrationBean 重复注册）。
 */
public class SpaForwardFilter implements jakarta.servlet.Filter {
    @Override
    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse resp,
                         jakarta.servlet.FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
        jakarta.servlet.http.HttpServletRequest request = (jakarta.servlet.http.HttpServletRequest) req;
        jakarta.servlet.http.HttpServletResponse response = (jakarta.servlet.http.HttpServletResponse) resp;
        String path = request.getRequestURI();
        if (shouldForward(path)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean shouldForward(String path) {
        return !path.startsWith("/api/")
                && !path.startsWith("/assets/")
                && !path.startsWith("/files/")
                && !path.equals("/index.html")
                && !path.contains(".");
    }
}
