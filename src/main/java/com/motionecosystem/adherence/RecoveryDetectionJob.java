package com.motionecosystem.adherence;

import com.motionecosystem.trainingplanning.api.ParticipantPlanWindowHistoryQueryPort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded scan; detection itself is idempotent and database-enforced. */
@Component
@RequiredArgsConstructor
class RecoveryDetectionJob {
    private final ParticipantPlanWindowHistoryQueryPort windows;
    private final RecoveryEpisodeService recovery;
    private final Clock clock;
    @Scheduled(fixedDelayString = "${moves.adherence.recovery-scan-delay-ms:3600000}")
    void scan() {
        int offset = 0;
        while (true) {
            var page = windows.participantsWithCompletedWindows(clock.instant(), 100, offset);
            page.forEach(recovery::detect);
            if (page.size() < 100) return;
            offset += page.size();
        }
    }
}
