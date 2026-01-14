package com.aditya.buyogo.controller;


import com.aditya.buyogo.dto.*;
import com.aditya.buyogo.services.EventService;
import com.aditya.buyogo.services.StateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/states")
public class StatesController {
    @Autowired
    private StateService stateService;
    @GetMapping
    public ApiResponse getEventByMachineId(@RequestParam("machineId") String machineId, @RequestParam("start") Instant start, @RequestParam("end") Instant end){
        StatesResponseDTO response = stateService.getEventByMachineIdAndDateBetween(machineId, start, end);
        return new ApiResponse(response);
    }

    @GetMapping("/top-defect-lines")
    public ApiResponse getTopDefectLines(@RequestParam("factoryId") String factoryId, @RequestParam("from") Instant from, @RequestParam("to") Instant to, @RequestParam("limit") Integer limit){
        List<TopDefectLineDTO> response = stateService.getTopDefectLines(factoryId,from, to, limit);
        return new ApiResponse(response);
    }
}
