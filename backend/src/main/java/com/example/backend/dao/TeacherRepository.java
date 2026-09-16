package com.example.backend.dao;

import com.example.backend.dao.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher,Integer> {

    Boolean existsTeacherByName(String name);

    Boolean existsTeacherByEmail(String email);

    Boolean existsTeacherByPhone(String phone);

    Teacher findTeacherByName(String name);

    Teacher findTeacherByEmail(String email);

    Teacher findTeacherByPhone(String phone);

    Boolean existsTeacherById(Integer id);

    Teacher findTeacherById(Integer id);
}
