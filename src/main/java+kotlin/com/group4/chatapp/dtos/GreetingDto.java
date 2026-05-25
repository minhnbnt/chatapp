package com.group4.chatapp.dtos;

/**
 * DTO chứa chuỗi lời chào trả về từ endpoint hello.
 *
 * DTO này chỉ mang một message đơn giản để client hiển thị ngay.
 */
public record GreetingDto(String message) {}
