package com.aditya.buyogo.controller;
import com.aditya.buyogo.dto.ApiResponse;
import com.aditya.buyogo.dto.BatchResponseDTO;
import com.aditya.buyogo.dto.EventDTO;
import com.aditya.buyogo.services.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/events")
public class EventIngestionController {
    @Autowired
    private EventService eventService;
    @PostMapping("/batch")
    public ApiResponse storeEvents(@RequestBody List<EventDTO> eventsData){
        BatchResponseDTO response = eventService.processEvents(eventsData);
        return new ApiResponse(response);
    }
}