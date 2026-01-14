package com.aditya.buyogo.dto;


import lombok.Data;

@Data
public class TopDefectLineDTO {
    private String lineId;
    private long totalDefects;
    private long eventCount;
    private double defectsPercent;
}
