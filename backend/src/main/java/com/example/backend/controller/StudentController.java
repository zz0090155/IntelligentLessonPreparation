package com.example.backend.controller;


import com.example.backend.dao.StudentPackRepository;
import com.example.backend.service.OssService;
import com.example.backend.service.StudentPacksService;
import com.example.backend.service.StudentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentPacksService studentPacksService;


    @PostMapping("/register")
    public ResponseEntity<Map<String,Object>> studentRegister(@RequestBody Map map){
        return studentService.studentRegister(map);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> studentLogin(@RequestBody Map map){
        return studentService.studentLogin(map);
    }

    @PostMapping("/reset_password")
    public ResponseEntity<Map<String,Object>> studentResetPassword(@RequestBody Map map){
        return studentService.studentResetPassword(map);
    }

    @PostMapping("/upload_and_generate")
    public ResponseEntity<?> uploadAndGenerate(@RequestParam("file") MultipartFile file, @RequestHeader(value = "Authorization", required = false) String authToken) {
        return studentPacksService.uploadAndGenerate(file,authToken);
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String,Object>> listStudentHistory(@RequestHeader(value = "Authorization")String authToken){
        return studentPacksService.listStudentHistory(authToken);
    }

    @GetMapping("/history/{pack_id}")
    public ResponseEntity<Map<String,Object>> getHistoryDetail(@RequestHeader(value = "Authorization")String authToken, @PathVariable("pack_id")Integer pack_id) throws JsonProcessingException {
        return studentPacksService.getHistoryDetail(authToken,pack_id);
    }

}
