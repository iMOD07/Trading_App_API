package com.mod.trading.model.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SettingsRequest {

    @Positive
    private double tradeAmount;

    @Positive
    private double rangeValue;

    @Positive
    private double profitPercent;

}