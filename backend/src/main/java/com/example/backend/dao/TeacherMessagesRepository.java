package com.example.backend.dao;

import com.example.backend.dao.entity.TeacherMessages;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherMessagesRepository extends JpaRepository<TeacherMessages,Integer> {

    List<TeacherMessages> findTeacherMessagesByConversationIdOrderByCreatedAtDesc(Integer conversationId);

    void deleteTeacherMessagesByConversationId(Integer conversationId);
}
