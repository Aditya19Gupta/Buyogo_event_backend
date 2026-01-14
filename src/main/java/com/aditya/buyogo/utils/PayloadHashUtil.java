package com.aditya.buyogo.utils;

import com.aditya.buyogo.dto.EventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class PayloadHashUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String generatePayloadHash(EventDTO event) {
        try {
            ObjectNode node = mapper.createObjectNode();

            node.put("eventId", event.getEventId());
            node.put("eventTime", event.getEventTime().toString());
            node.put("machineId", event.getMachineId());
            node.put("durationMs", event.getDurationMs());
            node.put("defectCount", event.getDefectCount());
            node.put("factoryId", event.getFactoryId());
            node.put("lineId", event.getLineId());

            String canonicalPayload = mapper.writeValueAsString(node);
            return sha256(canonicalPayload);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate payload hash", e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
