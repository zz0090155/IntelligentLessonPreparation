package com.example.backend.controller;

import com.example.backend.service.StudyPackExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StudyPackController {

    @Autowired
    private StudyPackExportService studyPackExportService;

    @PostMapping("/study_pack/export_docx")
    public ResponseEntity<?> exportStudyPackDoc(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authToken
    ) {
        return studyPackExportService.exportDocx(payload, authToken);
    }
}
