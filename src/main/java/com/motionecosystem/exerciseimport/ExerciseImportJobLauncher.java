package com.motionecosystem.exerciseimport;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ExerciseImportJobLauncher {
    private final JobLauncher launcher;
    @Qualifier("exerciseImportJob") private final Job job;

    @Async("exerciseImportExecutor")
    public void launch(UUID batchId) {
        try {
            launcher.run(job, new JobParametersBuilder().addString("batchId", batchId.toString(), true)
                    .toJobParameters());
        } catch (Exception failure) {
            throw new IllegalStateException("cannot launch exercise import batch " + batchId, failure);
        }
    }
}
