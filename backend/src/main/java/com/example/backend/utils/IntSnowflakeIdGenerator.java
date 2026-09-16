package com.example.backend.utils;

import java.util.concurrent.TimeUnit;

public class IntSnowflakeIdGenerator {
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public IntSnowflakeIdGenerator() {
    }

    public synchronized int nextId() {
        long timestamp = System.currentTimeMillis();
        long base = timestamp % 10000000;

        if (timestamp < lastTimestamp) {
            try {
                TimeUnit.MILLISECONDS.sleep(lastTimestamp - timestamp + 1);
                timestamp = System.currentTimeMillis();
                base = timestamp % 10000000;
            } catch (InterruptedException e) {
                throw new RuntimeException("生成ID时被中断", e);
            }
        }

        if (timestamp == lastTimestamp) {
            sequence++;
            if (sequence >= 100) {
                sequence = 0;
                timestamp++;
                base = timestamp % 10000000;
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        long id = base * 100 + sequence;
        return (int) id;
    }
}