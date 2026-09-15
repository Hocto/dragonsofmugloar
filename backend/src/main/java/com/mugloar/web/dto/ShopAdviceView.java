package com.mugloar.web.dto;

/** What the shop policy would do now, and why. */
public record ShopAdviceView(String action, String itemId, String reason) {
}
