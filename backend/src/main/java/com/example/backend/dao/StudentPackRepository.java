package com.example.backend.dao;

import com.example.backend.dao.entity.StudentPacks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentPackRepository extends JpaRepository<StudentPacks,Integer> {

    List<StudentPacks> findStudentPacksByStudentIdOrderByCreatedAtDesc(Integer studentId);

    StudentPacks findStudentPacksByStudentIdAndId(Integer studentId,Integer id);
}
