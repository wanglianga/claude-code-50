package cn.nightpharmacy.web;

import cn.nightpharmacy.domain.User;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简内存会话：登录换 token，请求头 X-Auth-Token 携带。
 */
@Component
public class SessionStore {

    public record Session(String token, User user) {}

    private final ConcurrentHashMap<String, User> tokens = new ConcurrentHashMap<>();

    public Session create(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, user);
        return new Session(token, user);
    }

    public Optional<User> resolve(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(tokens.get(token));
    }

    public void revoke(String token) {
        if (token != null) tokens.remove(token);
    }
}
