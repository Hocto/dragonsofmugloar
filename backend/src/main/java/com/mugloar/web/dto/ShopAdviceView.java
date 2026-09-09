package com.mugloar.web.dto;

/** What the shop policy would do right now, and the sentence explaining it. */
public record ShopAdviceView(String action, String itemId, String reason) {
}
