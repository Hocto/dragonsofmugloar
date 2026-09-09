package com.mugloar.web.dto;

public record ShopItemView(
        String id, String name, int cost, boolean affordable, boolean healing, boolean recommended) {
}
