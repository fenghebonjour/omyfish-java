package com.omyfish.identity.adapter.in.web.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
