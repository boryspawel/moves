package com.motionecosystem.availability;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecurringSlotRepository extends JpaRepository<RecurringSlot, UUID> {
    List<RecurringSlot> findByAccountIdOrderByDayOfWeekAscStartTimeAsc(UUID accountId);
    void deleteByAccountId(UUID accountId);
    boolean existsByAccountId(UUID accountId);
}
