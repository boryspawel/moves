package com.motionecosystem.exercisesets.ports;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.*;

/** Public application boundary for the exercise-set editor; it deliberately contains no JPA types. */
public interface ExerciseSetCommandPort {
    SetView create(String subject); List<SetView> list(String subject); SetView get(String subject, UUID setId);
    List<VersionSummary> listVersions(String subject, UUID setId); VersionView version(String subject, UUID setId, UUID versionId);
    VersionView currentDraft(String subject, UUID setId); VersionView latestPublished(String subject, UUID setId);
    AnalysisView analysis(String subject, UUID setId, UUID versionId);
    AnatomyAnalysisView anatomy(String subject, UUID setId, UUID versionId);
    VersionView updateMetadata(String subject, UUID setId, UUID versionId, MetadataRequest request);
    VersionView addItem(String subject, UUID setId, UUID versionId, ItemRequest request); VersionView updateItem(String subject, UUID setId, UUID versionId, UUID itemId, ItemRequest request);
    VersionView moveItem(String subject, UUID setId, UUID versionId, MoveRequest request); VersionView removeItem(String subject, UUID setId, UUID versionId, UUID itemId, long expectedVersion);
    VersionView publish(String subject, UUID setId, UUID versionId, long expectedVersion); VersionView nextDraft(String subject, UUID setId, UUID sourceVersionId);
    VersionView variantDraft(String subject, UUID setId, UUID sourceVersionId, CreateVariantRequest request); VersionView retire(String subject, UUID setId, UUID versionId);
}
