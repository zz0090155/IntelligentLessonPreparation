package com.example.backend.dao;

import com.example.backend.dao.entity.TeacherConversations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherConversationsRepository extends JpaRepository<TeacherConversations,Integer> {

    Boolean existsTeacherConversationsByTeacherIdAndConversationKey(Integer teacherId,String conversationKey);

    TeacherConversations findTeacherConversationsByTeacherIdAndConversationKey(Integer teacherId,String conversationKey);

    List<TeacherConversations> findTeacherConversationsByTeacherIdOrderByUpdatedAtDesc(Integer teacherId);
}
