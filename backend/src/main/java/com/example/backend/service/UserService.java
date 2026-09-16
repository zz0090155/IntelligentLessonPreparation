package com.example.backend.service;

import com.example.backend.utils.ErrorResult;
import com.example.backend.utils.RandomNumberGenerator;
import com.example.backend.utils.SendCodeUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private static final long CODE_TTL_MINUTES = 10;

    @Autowired
    private JavaMailSender javaMailSender;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    public ResponseEntity<Map<String,Object>> sendCodeByEmail(String email, String phone, String name, String account) {
        String normalizedEmail = asText(email);
        String normalizedPhone = asText(phone);
        String normalizedName = asText(name);
        String normalizedAccount = asText(account);
        String identity = firstNonBlank(normalizedAccount, normalizedName, normalizedEmail, normalizedPhone);

        if(identity.isEmpty()){
            return ResponseEntity.status(400).body(ErrorResult.error("请提供用户名、邮箱或手机号用于验证码绑定"));
        }

        String code = RandomNumberGenerator.generateValidateCode();
        storeCode(identity, code);
        if(!normalizedName.isEmpty()){
            storeCode(normalizedName, code);
        }
        if(!normalizedEmail.isEmpty()){
            storeCode(normalizedEmail, code);
        }
        if(!normalizedPhone.isEmpty()){
            storeCode(normalizedPhone, code);
        }

        Map<String,Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("identity", identity);
        result.put("expires_in_seconds", TimeUnit.MINUTES.toSeconds(CODE_TTL_MINUTES));

        String sendChannel = "none";
        boolean sent = false;

        String emailToSend = normalizedEmail;
        if(emailToSend.isEmpty() && isEmail(identity)) {
            emailToSend = identity;
        }

        if(!emailToSend.isEmpty()) {
            if(!isEmail(emailToSend)) {
                return ResponseEntity.status(400).body(ErrorResult.error("邮箱格式不正确"));
            }
            try {
                SendCodeUtils.sendEmailCode(javaMailSender, emailToSend, "您的注册验证码", code);
                sendChannel = "email";
                sent = true;
            } catch (Exception ignored) {
                sendChannel = "email_fallback";
                sent = false;
            }
        } else {
            String phoneToSend = normalizedPhone;
            if(phoneToSend.isEmpty() && isPhone(identity)) {
                phoneToSend = identity;
            }
            if(!phoneToSend.isEmpty()) {
                if(!isPhone(phoneToSend)) {
                    return ResponseEntity.status(400).body(ErrorResult.error("手机号格式不正确"));
                }
                sendChannel = "phone_mock";
                sent = false;
            } else {
                sendChannel = "identity_mock";
                sent = false;
            }
        }

        result.put("channel", sendChannel);
        result.put("sent", sent);
        result.put("code", sent ? null : code);
        return ResponseEntity.ok(result);
    }

    private void storeCode(String identity, String code) {
        String normalized = asText(identity);
        if(normalized.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(codeKey(normalized), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(normalized + "code", code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private String codeKey(String identity) {
        return "verify:code:" + identity.toLowerCase();
    }

    private boolean isEmail(String value) {
        String text = asText(value);
        return !text.isEmpty() && text.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private boolean isPhone(String value) {
        String text = asText(value);
        return !text.isEmpty() && text.matches("^1[3-9]\\d{9}$");
    }

    private String firstNonBlank(String... values) {
        if(values == null) {
            return "";
        }
        for (String value : values) {
            String text = asText(value);
            if(!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private String asText(Object value) {
        if(value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
