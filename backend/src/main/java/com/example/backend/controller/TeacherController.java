package com.example.backend.controller;

import com.example.backend.service.StudentPacksService;
import com.example.backend.service.TeacherService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private StudentPacksService studentPacksService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> teacherRegister(@RequestBody Map map) {
        return teacherService.teacherRegister(map);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> teacherLogin(@RequestBody Map map) {
        return teacherService.teacherLogin(map);
    }

    @PostMapping("/reset_password")
    public ResponseEntity<Map<String, Object>> teacherResetPassword(@RequestBody Map map) {
        return teacherService.teacherResetPassword(map);
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> teacherConversation(@RequestBody Map map, HttpServletRequest request) {
        return teacherService.teacherConversation(map, extractToken(request));
    }

    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> getTeacherConversation(HttpServletRequest request) {
        return teacherService.getTeacherConversation(extractToken(request));
    }

    @GetMapping("/conversations/{conversation_key}")
    public ResponseEntity<Map<String, Object>> getTeacherMessage(
            HttpServletRequest request,
            @PathVariable("conversation_key") String conversationKey
    ) {
        return teacherService.getTeacherMessage(extractToken(request), conversationKey);
    }

    @PatchMapping("/conversations/{conversation_key}/pin")
    public ResponseEntity<Map<String, Object>> setConversationPin(
            HttpServletRequest request,
            @PathVariable("conversation_key") String conversationKey,
            @RequestBody(required = false) Map payload
    ) {
        return teacherService.setConversationPin(extractToken(request), conversationKey, payload);
    }

    @DeleteMapping("/conversations/{conversation_key}")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            HttpServletRequest request,
            @PathVariable("conversation_key") String conversationKey
    ) {
        return teacherService.deleteConversation(extractToken(request), conversationKey);
    }

    @PostMapping("/upload_and_generate")
    public ResponseEntity<?> uploadAndGenerate(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authToken
    ) {
        return studentPacksService.uploadAndGenerateForTeacher(file, authToken);
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> listTeacherHistory(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authToken
    ) {
        return studentPacksService.listTeacherHistory(authToken);
    }

    @GetMapping("/history/{pack_id}")
    public ResponseEntity<Map<String, Object>> getTeacherHistoryDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authToken,
            @PathVariable("pack_id") Integer packId
    ) throws JsonProcessingException {
        return studentPacksService.getTeacherHistoryDetail(authToken, packId);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return "";
        }
        String text = header.trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return text.substring(7).trim();
        }
        return text;
    }
}
