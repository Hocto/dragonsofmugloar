package com.mugloar.adapter.mugloar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopItemResponse(String id, String name, int cost) {
}
