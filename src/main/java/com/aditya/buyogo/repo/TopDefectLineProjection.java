package com.aditya.buyogo.repo;

public interface TopDefectLineProjection {
    String getLineId();
    long getTotalDefects();
    long getEventCount();
}
