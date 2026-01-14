package com.aditya.buyogo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;
    public ApiResponse(Object responseDTO){
        this.data = responseDTO;
        this.success = true;
        this.message = "success";
    }
}
