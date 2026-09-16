package com.example.backend.utils;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SendCodeUtils {

    public static void sendEmailCode(JavaMailSender mailSender, String email, String subject, String code) throws MailSendException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("1152974119@qq.com");//发件人
        message.setSubject(subject);//邮件主题
        message.setText("您的验证码为：" + code+"，验证码有效期为10分钟，请勿泄露");//邮件内容
        message.setTo(email);//收件人
        mailSender.send(message);
    }
}