package com.assetmanagement.tracking.repository;
import com.assetmanagement.tracking.domain.RollCallItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RollCallItemRepository extends JpaRepository<RollCallItem, UUID> {
    List<RollCallItem> findAllBySessionId(UUID sessionId);
    long countBySessionIdAndPresentTrue(UUID sessionId);
}
