package com.dev.HiddenBATHAuto.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageRestController {

    private final MessageController messageController;

    public MessageRestController(MessageController messageController) {
        this.messageController = messageController;
    }

    @PostMapping("/sendToGuests")
    public void sendToGuests(@RequestBody Map<String, String> payload) {
        messageController.sendToGuests(payload.get("message"));
    }

    @PostMapping("/sendToMember")
    public void sendToMember(@RequestBody Map<String, String> payload) {
        messageController.sendToMember(payload.get("message"));
    }
}