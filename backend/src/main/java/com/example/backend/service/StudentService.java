package com.example.backend.service;

import com.example.backend.dao.StudentRepository;
import com.example.backend.dao.entity.Student;
import com.example.backend.utils.ErrorResult;
import com.example.backend.utils.HashUtils;
import com.example.backend.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    public ResponseEntity<Map<String,Object>> studentRegister(Map map){
        String name = asText(map.get("name"));
        String code = asText(map.get("code"));
        String password = asText(map.get("password"));

        if(name.isEmpty()){
            return ResponseEntity.status(400).body(ErrorResult.error("用户名不能为空"));
        }
        if(password.isEmpty()){
            return ResponseEntity.status(400).body(ErrorResult.error("密码不能为空"));
        }
        if(!validateCodeIfProvided(name, code)){
            return ResponseEntity.status(400).body(ErrorResult.error("验证码错误或验证码已过期"));
        }

        if(studentRepository.existsStudentByName(name)){
            return ResponseEntity.status(400).body(ErrorResult.error("学生已存在"));
        }

        Student student=new Student();
        student.setName(name);
        student.setEmail(asNullableText(map.get("email")));
        student.setPhone(asNullableText(map.get("phone")));
        student.setPasswordHash(HashUtils.hashPasswordWithFixedSalt(password));
        studentRepository.save(student);

        Map<String,Object> map1=new HashMap<>();
        map1.put("status","success");
        return ResponseEntity.ok(map1);
    }

    public ResponseEntity<Map<String,Object>> studentLogin(Map map){
        String name = asText(map.get("name"));
        String code = asText(map.get("code"));
        String password = asText(map.get("password"));

        if(name.isEmpty()){
            return ResponseEntity.status(400).body(ErrorResult.error("用户名不能为空"));
        }
        if(password.isEmpty()){
            return ResponseEntity.status(400).body(ErrorResult.error("密码不能为空"));
        }
        if(!validateCodeIfProvided(name, code)){
            return ResponseEntity.status(400).body(ErrorResult.error("验证码错误或验证码已过期"));
        }

        if(!studentRepository.existsStudentByName(name)){
            return ResponseEntity.status(400).body(ErrorResult.error("用户名错误"));
        }

        Student student=studentRepository.findStudentByName(name);
        if(!HashUtils.verifyPasswordWithFixedSalt(password,student.getPasswordHash())){
            return ResponseEntity.status(400).body(ErrorResult.error("用户密码错误"));
        }

        Map<String,Object> map1=new HashMap<>();
        map1.put("id",student.getId());
        map1.put("name",student.getName());

        String token= JwtUtils.createToken(student.getId());
        Map<String,Object> map2=new HashMap<>();
        map2.put("status","success");
        map2.put("token",token);
        map2.put("user",map1);
        return ResponseEntity.ok(map2);
    }

    public ResponseEntity<Map<String,Object>> studentResetPassword(Map map){
        String name = asText(map.get("name"));
        String newPassword = asText(map.get("new_password"));

        if(name.isEmpty()){
            return ResponseEntity.status(400).body(ErrorResult.error("用户名不能为空"));
        }
        if(newPassword.length() < 6){
            return ResponseEntity.status(400).body(ErrorResult.error("新密码至少 6 位"));
        }

        Student student = studentRepository.findStudentByName(name);
        if(student == null){
            return ResponseEntity.status(404).body(ErrorResult.error("用户不存在"));
        }

        student.setPasswordHash(HashUtils.hashPasswordWithFixedSalt(newPassword));
        studentRepository.save(student);

        Map<String,Object> result = new HashMap<>();
        result.put("status","success");
        return ResponseEntity.ok(result);
    }

    private boolean validateCodeIfProvided(String name, String code) {
        String normalizedCode = asText(code);
        if (normalizedCode.isEmpty()) {
            return true;
        }
        String codeInRedis = (String) redisTemplate.opsForValue().get(asText(name) + "code");
        return normalizedCode.equals(asText(codeInRedis));
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

}
