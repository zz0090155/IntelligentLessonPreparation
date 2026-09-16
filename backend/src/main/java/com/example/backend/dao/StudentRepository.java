package com.example.backend.dao;

import com.example.backend.dao.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student,Integer> {

    Boolean existsStudentByName(String name);

    Student findStudentByName(String name);

    Student findStudentById(Integer id);

    Boolean existsStudentById(Integer id);
}
