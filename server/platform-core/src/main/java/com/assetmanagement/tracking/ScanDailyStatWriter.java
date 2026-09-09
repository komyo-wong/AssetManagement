package com.assetmanagement.tracking;

import com.assetmanagement.tracking.repository.ScanDailyStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class ScanDailyStatWriter {

    private final ScanDailyStatRepository repository;

    public ScanDailyStatWriter(ScanDailyStatRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(UUID tenantId, UUID projectId, Instant at) {
        LocalDate day = (at == null ? Instant.now() : at).atZone(ZoneOffset.UTC).toLocalDate();
        repository.increment(tenantId, projectId, day);
    }
}
