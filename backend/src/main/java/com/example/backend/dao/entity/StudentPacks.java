package com.example.backend.dao.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_packs")
@EntityListeners(AuditingEntityListener.class)
public class StudentPacks {

    @Id
    @GenericGenerator(
            name = "intSnowflakeGenerator",
            strategy = "com.example.backend.utils.IntSnowflakeHibernateGenerator"
    )
    @GeneratedValue(generator = "intSnowflakeGenerator")
    private Integer id;

    private Integer studentId;
    private String filename;
    private String outline;
    private String flashcardsJson;
    private String quizJson;

    @CreatedDate
    private LocalDateTime createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getStudentId() {
        return studentId;
    }

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public String getFlashcardsJson() {
        return flashcardsJson;
    }

    public void setFlashcardsJson(String flashcardsJson) {
        this.flashcardsJson = flashcardsJson;
    }

    public String getQuizJson() {
        return quizJson;
    }

    public void setQuizJson(String quizJson) {
        this.quizJson = quizJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
