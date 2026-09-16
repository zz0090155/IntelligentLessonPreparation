package com.example.backend.service;

import com.example.backend.dao.TeacherConversationsRepository;
import com.example.backend.dao.TeacherMessagesRepository;
import com.example.backend.dao.TeacherRepository;
import com.example.backend.dao.entity.Teacher;
import com.example.backend.dao.entity.TeacherConversations;
import com.example.backend.dao.entity.TeacherMessages;
import com.example.backend.utils.ErrorResult;
import com.example.backend.utils.HashUtils;
import com.example.backend.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class TeacherService {

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherConversationsRepository teacherConversationsRepository;

    @Autowired
    private TeacherMessagesRepository teacherMessagesRepository;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${aliyun.oss.domain}")
    private String ossDomain;

    private static final String CONVERSATION_PIN_KEY_PREFIX = "teacher:conversation:pin:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "teacher:login:fail:";
    private static final String LOGIN_LOCK_KEY_PREFIX = "teacher:login:lock:";
    private static final String REGISTER_FAIL_KEY_PREFIX = "teacher:register:fail:";
    private static final String REGISTER_LOCK_KEY_PREFIX = "teacher:register:lock:";
    private static final String VERIFY_CODE_KEY_PREFIX = "verify:code:";
    private static final int LOGIN_MAX_FAILURES = 5;
    private static final int REGISTER_MAX_FAILURES = 5;
    private static final long LOCK_MINUTES = 15L;
    private static final String GENERIC_LOGIN_ERROR = "账号或凭证错误";

    public ResponseEntity<Map<String, Object>> teacherRegister(Map map) {
        String name = asText(map.get("name"));
        String email = normalizeEmail(asText(map.get("email")));
        String phone = asText(map.get("phone"));
        String account = asText(map.get("account"));
        String code = asText(map.get("code"));
        String password = asText(map.get("password"));
        String confirmPassword = firstNonBlank(
                asText(map.get("confirm_password")),
                asText(map.get("confirmPassword")),
                asText(map.get("repeat_password")),
                asText(map.get("password_confirm")),
                password
        );

        String principal = normalizedPrincipal(firstNonBlank(account, email, phone, name));
        if (principal.isEmpty()) {
            principal = "anonymous";
        }
        if (isLocked(registerLockKey(principal))) {
            return lockResponse("注册失败次数过多，请稍后再试", registerLockKey(principal));
        }

        if (name.isEmpty()) {
            return registerValidationFailure(principal, "用户名不能为空");
        }
        if (password.isEmpty()) {
            return registerValidationFailure(principal, "密码不能为空");
        }
        if (!password.equals(confirmPassword)) {
            return registerValidationFailure(principal, "两次输入密码必须一致");
        }
        if (email.isEmpty() && phone.isEmpty()) {
            return registerValidationFailure(principal, "邮箱/手机号至少填写一项");
        }
        if (!email.isEmpty() && !isEmail(email)) {
            return registerValidationFailure(principal, "邮箱格式不正确");
        }
        if (!phone.isEmpty() && !isPhone(phone)) {
            return registerValidationFailure(principal, "手机号格式不正确");
        }
        if (code.isEmpty()) {
            return registerValidationFailure(principal, "验证码不能为空");
        }

        if (!verifyCode(code, name, email, phone, account)) {
            return registerValidationFailure(principal, "验证码错误或已过期");
        }
        if (Boolean.TRUE.equals(teacherRepository.existsTeacherByName(name))) {
            return registerValidationFailure(principal, "教师已存在");
        }
        if (!email.isEmpty() && Boolean.TRUE.equals(teacherRepository.existsTeacherByEmail(email))) {
            return registerValidationFailure(principal, "邮箱已被注册");
        }
        if (!phone.isEmpty() && Boolean.TRUE.equals(teacherRepository.existsTeacherByPhone(phone))) {
            return registerValidationFailure(principal, "手机号已被注册");
        }

        Teacher teacher = new Teacher();
        teacher.setName(name);
        teacher.setEmail(asNullableText(email));
        teacher.setPhone(asNullableText(phone));
        teacher.setPasswordHash(HashUtils.hashPasswordWithFixedSalt(password));
        teacherRepository.save(teacher);

        clearFailures(registerFailKey(principal), registerLockKey(principal));
        consumeCode(name, email, phone, account);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("teacher_id", teacher.getId());
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> teacherLogin(Map map) {
        String name = asText(map.get("name"));
        String email = normalizeEmail(asText(map.get("email")));
        String phone = asText(map.get("phone"));
        String account = asText(map.get("account"));
        String code = asText(map.get("code"));
        String password = asText(map.get("password"));

        String identity = firstNonBlank(account, name, email, phone);
        if (identity.isEmpty()) {
            return ResponseEntity.status(400).body(ErrorResult.error("请填写用户名、邮箱或手机号"));
        }
        if (password.isEmpty() && code.isEmpty()) {
            return ResponseEntity.status(400).body(ErrorResult.error("密码和验证码至少填写一项"));
        }

        String principal = normalizedPrincipal(identity);
        if (isLocked(loginLockKey(principal))) {
            return lockResponse("登录失败次数过多，请稍后再试", loginLockKey(principal));
        }

        Teacher teacher = findTeacherByAccount(identity);
        if (teacher == null) {
            return loginFailure(principal, GENERIC_LOGIN_ERROR);
        }

        boolean passedByPassword = !password.isEmpty() && HashUtils.verifyPasswordWithFixedSalt(password, teacher.getPasswordHash());
        boolean passedByCode = !code.isEmpty() && verifyCodeForTeacher(code, teacher, identity);

        if (!(passedByPassword || passedByCode)) {
            return loginFailure(principal, GENERIC_LOGIN_ERROR);
        }

        clearFailures(loginFailKey(principal), loginLockKey(principal));
        consumeCode(identity, teacher.getName(), teacher.getEmail(), teacher.getPhone());

        Map<String, Object> user = new HashMap<>();
        user.put("id", teacher.getId());
        user.put("name", teacher.getName());
        user.put("email", teacher.getEmail());
        user.put("phone", teacher.getPhone());

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("token", JwtUtils.createToken(teacher.getId()));
        result.put("user", user);
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> teacherResetPassword(Map map) {
        String name = asText(map.get("name"));
        String email = normalizeEmail(asText(map.get("email")));
        String phone = asText(map.get("phone"));
        String account = asText(map.get("account"));
        String code = asText(map.get("code"));
        String newPassword = asText(map.get("new_password"));

        String identity = firstNonBlank(account, name, email, phone);

        if (identity.isEmpty()) {
            return ResponseEntity.status(400).body(ErrorResult.error("请填写用户名、邮箱或手机号"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.status(400).body(ErrorResult.error("新密码至少 6 位"));
        }

        Teacher teacher = findTeacherByAccount(identity);
        if (teacher == null) {
            return ResponseEntity.status(404).body(ErrorResult.error("用户不存在"));
        }

        if (!code.isEmpty() && !verifyCodeForTeacher(code, teacher, identity)) {
            return ResponseEntity.status(400).body(ErrorResult.error("验证码错误或已过期"));
        }

        teacher.setPasswordHash(HashUtils.hashPasswordWithFixedSalt(newPassword));
        teacherRepository.save(teacher);

        String principal = normalizedPrincipal(identity);
        clearFailures(loginFailKey(principal), loginLockKey(principal));
        consumeCode(identity, teacher.getName(), teacher.getEmail(), teacher.getPhone());

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> teacherConversation(Map map, String token) {
        Integer teacherId = resolveTeacherId(token);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }

        String conversationKey = asText(map.get("conversation_key"));
        if (conversationKey.isEmpty()) {
            return ResponseEntity.status(400).body(ErrorResult.error("conversation_key 不能为空"));
        }
        String title = asText(map.get("title"));

        TeacherConversations conversation = teacherConversationsRepository
                .findTeacherConversationsByTeacherIdAndConversationKey(teacherId, conversationKey);
        if (conversation == null) {
            conversation = new TeacherConversations();
            conversation.setTeacherId(teacherId);
            conversation.setConversationKey(conversationKey);
            conversation.setTitle(title.isEmpty() ? "新对话" : title);
        } else if (!title.isEmpty()) {
            conversation.setTitle(title);
        }
        teacherConversationsRepository.save(conversation);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> getTeacherConversation(String token) {
        Integer teacherId = resolveTeacherId(token);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }

        List<TeacherConversations> conversations = new ArrayList<>(
                teacherConversationsRepository.findTeacherConversationsByTeacherIdOrderByUpdatedAtDesc(teacherId)
        );
        conversations.sort(
                Comparator.<TeacherConversations, Boolean>comparing(
                                conversation -> isConversationPinned(teacherId, conversation.getConversationKey())
                        )
                        .reversed()
                        .thenComparing(TeacherConversations::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        );

        List<Map<String, Object>> responseItems = new ArrayList<>();
        for (TeacherConversations conversation : conversations) {
            List<TeacherMessages> messages = teacherMessagesRepository
                    .findTeacherMessagesByConversationIdOrderByCreatedAtDesc(conversation.getId());

            Map<String, Object> item = new HashMap<>();
            item.put("conversation_key", conversation.getConversationKey());
            item.put("title", conversation.getTitle());
            item.put("update_at", conversation.getUpdatedAt());
            item.put("pinned", isConversationPinned(teacherId, conversation.getConversationKey()));
            if (messages.isEmpty()) {
                item.put("last_message", "");
            } else {
                String content = asText(messages.get(0).getContent());
                item.put("last_message", content.length() > 80 ? content.substring(0, 80) : content);
            }
            responseItems.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("conversations", responseItems);
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> getTeacherMessage(String token, String conversationKey) {
        Integer teacherId = resolveTeacherId(token);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> messageItems = new ArrayList<>();
        TeacherConversations conversation = teacherConversationsRepository
                .findTeacherConversationsByTeacherIdAndConversationKey(teacherId, conversationKey);
        if (conversation == null) {
            result.put("status", "success");
            result.put("messages", messageItems);
            return ResponseEntity.ok(result);
        }

        List<TeacherMessages> messages = teacherMessagesRepository
                .findTeacherMessagesByConversationIdOrderByCreatedAtDesc(conversation.getId());
        for (TeacherMessages message : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            item.put("message_type", message.getMessageType());
            item.put("media_url", resolveMediaUrl(message.getMediaUrl()));
            item.put("created_at", message.getCreatedAt());
            messageItems.add(item);
        }

        result.put("status", "success");
        result.put("messages", messageItems);
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> setConversationPin(String token, String conversationKey, Map payload) {
        Integer teacherId = resolveTeacherId(token);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }

        TeacherConversations conversation = teacherConversationsRepository
                .findTeacherConversationsByTeacherIdAndConversationKey(teacherId, conversationKey);
        if (conversation == null) {
            return ResponseEntity.status(404).body(ErrorResult.error("对话不存在"));
        }

        boolean pinned = toBoolean(payload == null ? null : payload.get("pinned"));
        String redisKey = conversationPinKey(teacherId, conversationKey);
        if (pinned) {
            redisTemplate.opsForValue().set(redisKey, "1");
        } else {
            redisTemplate.delete(redisKey);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("conversation_key", conversationKey);
        result.put("pinned", pinned);
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<Map<String, Object>> deleteConversation(String token, String conversationKey) {
        Integer teacherId = resolveTeacherId(token);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }

        TeacherConversations conversation = teacherConversationsRepository
                .findTeacherConversationsByTeacherIdAndConversationKey(teacherId, conversationKey);
        if (conversation == null) {
            return ResponseEntity.status(404).body(ErrorResult.error("对话不存在"));
        }

        teacherMessagesRepository.deleteTeacherMessagesByConversationId(conversation.getId());
        teacherConversationsRepository.delete(conversation);
        redisTemplate.delete(conversationPinKey(teacherId, conversationKey));

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        return ResponseEntity.ok(result);
    }

    private Integer resolveTeacherId(String token) {
        String normalizedToken = normalizeToken(token);
        if (normalizedToken == null || normalizedToken.isEmpty()) {
            return null;
        }
        try {
            if (!JwtUtils.checkToken(normalizedToken)) {
                return null;
            }
            int teacherId = JwtUtils.getIdFromJwt(normalizedToken);
            return teacherRepository.existsTeacherById(teacherId) ? teacherId : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private String resolveMediaUrl(String mediaUrl) {
        String value = asText(mediaUrl);
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return ossDomain + "/" + value;
    }

    private String conversationPinKey(Integer teacherId, String conversationKey) {
        return CONVERSATION_PIN_KEY_PREFIX + teacherId + ":" + conversationKey;
    }

    private boolean isConversationPinned(Integer teacherId, String conversationKey) {
        Object value = redisTemplate.opsForValue().get(conversationPinKey(teacherId, conversationKey));
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private ResponseEntity<Map<String, Object>> registerValidationFailure(String principal, String detail) {
        recordFailure(registerFailKey(principal), registerLockKey(principal), REGISTER_MAX_FAILURES);
        if (isLocked(registerLockKey(principal))) {
            return lockResponse("注册失败次数过多，请稍后再试", registerLockKey(principal));
        }
        return ResponseEntity.status(400).body(ErrorResult.error(detail));
    }

    private ResponseEntity<Map<String, Object>> loginFailure(String principal, String detail) {
        recordFailure(loginFailKey(principal), loginLockKey(principal), LOGIN_MAX_FAILURES);
        if (isLocked(loginLockKey(principal))) {
            return lockResponse("登录失败次数过多，请稍后再试", loginLockKey(principal));
        }
        return ResponseEntity.status(400).body(ErrorResult.error(detail));
    }

    private String registerFailKey(String principal) {
        return REGISTER_FAIL_KEY_PREFIX + normalizedPrincipal(principal);
    }

    private String registerLockKey(String principal) {
        return REGISTER_LOCK_KEY_PREFIX + normalizedPrincipal(principal);
    }

    private String loginFailKey(String principal) {
        return LOGIN_FAIL_KEY_PREFIX + normalizedPrincipal(principal);
    }

    private String loginLockKey(String principal) {
        return LOGIN_LOCK_KEY_PREFIX + normalizedPrincipal(principal);
    }

    private void recordFailure(String failKey, String lockKey, int maxFailures) {
        Long failureCount = redisTemplate.opsForValue().increment(failKey);
        if (failureCount != null && failureCount <= 1L) {
            redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
        }
        if (failureCount != null && failureCount >= maxFailures) {
            redisTemplate.opsForValue().set(lockKey, "1", LOCK_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(failKey);
        }
    }

    private void clearFailures(String failKey, String lockKey) {
        redisTemplate.delete(failKey);
        redisTemplate.delete(lockKey);
    }

    private boolean isLocked(String lockKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    private ResponseEntity<Map<String, Object>> lockResponse(String detail, String lockKey) {
        Long retrySeconds = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        long waitSeconds = (retrySeconds == null || retrySeconds < 0)
                ? TimeUnit.MINUTES.toSeconds(LOCK_MINUTES)
                : retrySeconds;

        Map<String, Object> payload = ErrorResult.error(detail);
        payload.put("retry_after_seconds", waitSeconds);
        return ResponseEntity.status(429).body(payload);
    }

    private Teacher findTeacherByAccount(String identity) {
        String normalized = asText(identity);
        if (normalized.isEmpty()) {
            return null;
        }
        String normalizedEmail = normalizeEmail(normalized);

        if (isEmail(normalizedEmail)) {
            Teacher byEmail = teacherRepository.findTeacherByEmail(normalizedEmail);
            if (byEmail != null) {
                return byEmail;
            }
        }
        if (isPhone(normalized)) {
            Teacher byPhone = teacherRepository.findTeacherByPhone(normalized);
            if (byPhone != null) {
                return byPhone;
            }
        }

        Teacher byName = teacherRepository.findTeacherByName(normalized);
        if (byName != null) {
            return byName;
        }

        Teacher byEmail = teacherRepository.findTeacherByEmail(normalizedEmail);
        if (byEmail != null) {
            return byEmail;
        }

        return teacherRepository.findTeacherByPhone(normalized);
    }

    private boolean verifyCodeForTeacher(String code, Teacher teacher, String identity) {
        if (teacher == null) {
            return false;
        }
        return verifyCode(code, identity, teacher.getName(), teacher.getEmail(), teacher.getPhone());
    }

    private boolean verifyCode(String code, String... identities) {
        String normalizedCode = asText(code);
        if (normalizedCode.isEmpty()) {
            return false;
        }
        for (String key : resolveCodeKeys(identities)) {
            Object codeInRedis = redisTemplate.opsForValue().get(key);
            if (normalizedCode.equals(asText(codeInRedis))) {
                return true;
            }
        }
        return false;
    }

    private void consumeCode(String... identities) {
        for (String key : resolveCodeKeys(identities)) {
            redisTemplate.delete(key);
        }
    }

    private Set<String> resolveCodeKeys(String... identities) {
        Set<String> keys = new LinkedHashSet<>();
        if (identities == null) {
            return keys;
        }
        for (String identity : identities) {
            String normalized = asText(identity);
            if (normalized.isEmpty()) {
                continue;
            }
            keys.add(VERIFY_CODE_KEY_PREFIX + normalized.toLowerCase(Locale.ROOT));
            keys.add(normalized + "code");
        }
        return keys;
    }

    private String normalizedPrincipal(String principal) {
        return asText(principal).toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String text = asText(value);
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private boolean isEmail(String value) {
        String text = asText(value);
        return !text.isEmpty() && text.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private boolean isPhone(String value) {
        String text = asText(value);
        return !text.isEmpty() && text.matches("^1[3-9]\\d{9}$");
    }

    private String normalizeEmail(String email) {
        return asText(email).toLowerCase(Locale.ROOT);
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String asText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String asNullableText(Object value) {
        String text = asText(value);
        return text.isEmpty() ? null : text;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = asText(value).toLowerCase(Locale.ROOT);
        return "1".equals(text) || "true".equals(text) || "yes".equals(text) || "on".equals(text);
    }
}
