package cn.nightpharmacy.web;

import cn.nightpharmacy.domain.User;
import cn.nightpharmacy.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

    private final UserRepository users;
    private final SessionStore sessions;

    public AuthController(UserRepository users, SessionStore sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    public record LoginReq(String username, String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        User u = users.findByUsername(req.username() == null ? "" : req.username().trim())
                .orElseThrow(() -> new ApiException(401, "用户名或密码错误"));
        if (!u.getPassword().equals(req.password()))
            throw new ApiException(401, "用户名或密码错误");
        SessionStore.Session s = sessions.create(u);
        return Map.of("success", true, "token", s.token(), "user", userView(u));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return Map.of("success", true, "user", userView(currentUser(request)));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        sessions.revoke(request.getHeader("X-Auth-Token"));
        return Map.of("success", true);
    }

    static Map<String, Object> userView(User u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "displayName", u.getDisplayName(),
                "role", u.getRole(),
                "phone", u.getPhone() == null ? "" : u.getPhone(),
                "licenseNo", u.getLicenseNo() == null ? "" : u.getLicenseNo());
    }
}
