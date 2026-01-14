package com.aditya.buyogo.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchResponseDTO {
    private Integer accepted;
    private Integer deduped;
    private Integer updated;
    private Integer rejected;
    private List<RejectionDTO> rejections;
}
