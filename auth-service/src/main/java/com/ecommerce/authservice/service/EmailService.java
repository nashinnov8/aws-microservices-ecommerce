package com.ecommerce.authservice.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private JavaMailSender sender;

    public EmailService(JavaMailSender sender) {
        this.sender = sender;
    }

    @Async
    public void sendMail(String to, String subject, String body) {
        // Implementation for sending email
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            sender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to send email");
        }
    }

}
