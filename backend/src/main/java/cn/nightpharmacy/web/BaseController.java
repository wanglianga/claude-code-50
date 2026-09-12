package cn.nightpharmacy.web;

import cn.nightpharmacy.domain.User;
import jakarta.servlet.http.HttpServletRequest;

public abstract class BaseController {
    protected User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute(AuthInterceptor.CURRENT_USER);
        if (user == null) throw new ApiException(401, "未登录");
        return user;
    }

    protected void requireRole(User user, String... roles) {
        for (String r : roles) {
            if (r.equals(user.getRole())) return;
        }
        throw new ApiException(403, "当前角色无权执行该操作");
    }
}
