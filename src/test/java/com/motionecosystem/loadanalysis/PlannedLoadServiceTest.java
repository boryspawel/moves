package com.motionecosystem.loadanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.MovementPatternValue;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadCalculationVersion;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.*;
import org.junit.jupiter.api.Test;

class PlannedLoadServiceTest {
    @Test
    void batchesCatalogProfilesAndReusesSnapshotForSameInputVersion() {
        ExerciseCatalogQueryPort catalog = mock(ExerciseCatalogQueryPort.class);
        AnatomyReferenceQueryPort anatomy = mock(AnatomyReferenceQueryPort.class);
        LoadAnalysisPersistence persistence = mock(LoadAnalysisPersistence.class);
        UUID firstVersion = UUID.randomUUID(); UUID secondVersion = UUID.randomUUID();
        when(catalog.findPublishedVersions(any())).thenReturn(Map.of(
                firstVersion, exercise(firstVersion), secondVersion, exercise(secondVersion)));
        AtomicReference<LoadProfile> stored = new AtomicReference<>();
        when(persistence.find(any(), any(), any(), any())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(persistence.save(any())).thenAnswer(call -> { LoadProfile value=call.getArgument(0); stored.set(value); return value; });
        PlannedLoadService service = new PlannedLoadService(catalog, anatomy, persistence,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        PlanRevisionSnapshot plan = plan(firstVersion, secondVersion);

        LoadProfile first = service.calculate(plan, new LoadCalculationVersion("v1", "default"));
        LoadProfile retry = service.calculate(plan, new LoadCalculationVersion("v1", "default"));

        assertThat(retry.snapshotId()).isEqualTo(first.snapshotId());
        verify(catalog, times(2)).findPublishedVersions(Set.of(firstVersion, secondVersion));
        verify(catalog, never()).findPublishedVersion(any());
        verify(persistence, times(1)).save(any());
    }

    private static PublishedExerciseVersionSnapshot exercise(UUID id) {
        return new PublishedExerciseVersionSnapshot(UUID.randomUUID(), "exercise", id, 1, 2,
                Set.of(MovementPatternValue.SQUAT), Set.of(), List.of(), List.of());
    }

    private static PlanRevisionSnapshot plan(UUID first, UUID second) {
        UUID revision=UUID.randomUUID(), plan=UUID.randomUUID(), cycle=UUID.randomUUID();
        UUID micro=UUID.randomUUID(), session=UUID.randomUUID();
        var prescriptions = List.of(prescription(first,1), prescription(second,2));
        return new PlanRevisionSnapshot(revision,plan,UUID.randomUUID(),1,null,0,"DRAFT",UUID.randomUUID(),
                "SPECIALIST",Instant.EPOCH,"NATIVE_V2","NOT_ASSESSED","phase",null,null,List.of(),
                List.of(new CycleSnapshot(cycle,1,"cycle",null,null,"intent","goal",List.of(
                        new MicrocycleSnapshot(micro,1,"micro",null,null,"intent","goal",List.of(
                                new SessionSnapshot(session,"session", LocalDate.of(2026,8,1),null,null,
                                        60,"DRAFT",prescriptions)))))),List.of());
    }

    private static PrescriptionSnapshot prescription(UUID exercise, int position) {
        return new PrescriptionSnapshot(UUID.randomUUID(),exercise,position,"BILATERAL","DYNAMIC_RESISTANCE",
                3,8,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }
}
