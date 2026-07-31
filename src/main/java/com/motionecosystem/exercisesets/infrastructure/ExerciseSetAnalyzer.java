package com.motionecosystem.exercisesets.infrastructure;

import java.util.*;
import java.math.BigDecimal;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.*;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.*;

/** Pure, snapshot-only policy. It intentionally never reads the live exercise catalogue. */
final class ExerciseSetAnalyzer {
    static final String POLICY_VERSION = "exercise-set-policy-v1";
    static final String VISUAL_CONCENTRATION_POLICY_VERSION = "visual-region-concentration-policy-v1";
    private static final String RULE_VERSION = "v1";

    AnalysisView analyze(ExerciseSetEntities.ExerciseSetVersionEntity version, boolean draft) {
        var findings = new ArrayList<AnalysisFinding>();
        var items = version.items.stream().sorted(Comparator.comparingInt(i -> i.position)).toList();
        if (blank(version.title)) suggest(findings, "TITLE_REQUIRED", "title", null, List.of());
        if (version.profile == null) suggest(findings, "PROFILE_REQUIRED", "profile", null, List.of());
        if (items.isEmpty()) suggest(findings, "ITEMS_REQUIRED", "items", null, List.of());
        Phase previous = null;
        var phases = EnumSet.noneOf(Phase.class);
        for (var item : items) {
            if (item.phase == null) suggest(findings, "PHASE_REQUIRED", "phase", null, ids(item)); else phases.add(item.phase);
            if (item.dose == null || item.exerciseVersionId == null) suggest(findings, "INVALID_ITEM", "items", item.phase, ids(item));
            if (previous != null && item.phase != null && item.phase.ordinal() < previous.ordinal()) suggest(findings, "INVALID_PHASE_ORDER", "phase", item.phase, ids(item));
            if (item.phase != null) previous = item.phase;
        }
        requiredPhases(version.profile, phases, findings);
        duplicateRules(items, findings);
        equipmentRules(items, findings);
        var time = time(items, findings);
        findings.sort(Comparator.comparing(AnalysisFinding::code).thenComparing(f -> f.itemIds().toString()));
        var status = findings.isEmpty() ? AnalysisStatus.NO_SUGGESTIONS : AnalysisStatus.SUGGESTIONS_AVAILABLE;
        return new AnalysisView(status, POLICY_VERSION, version.version, null, draft, !draft,
                new AnalysisMetrics(items.size(), time.seconds, time.confidence, transitions(items), doseSwitches(items)), findings);
    }

    AnatomyAnalysisView analyzeAnatomy(ExerciseSetEntities.ExerciseSetVersionEntity version, boolean draft,
                                       Map<UUID, ItemAnatomySnapshot> snapshots) {
        var missing = new ArrayList<AnatomyMissingData>();
        var lateralityFindings = new ArrayList<AnatomyFinding>();
        var patterns = new TreeMap<String, List<UUID>>();
        var grouped = new TreeMap<String, Map<StructureSideKey, List<AnatomyContributionBreakdown>>>();
        var aggregateCandidates = new ArrayList<AggregateCandidate>();
        var visualCandidates = new ArrayList<VisualCandidate>();
        for (var item : version.items.stream().sorted(Comparator.comparingInt(i -> i.position)).toList()) {
            var snapshot = snapshots.get(item.id);
            if (snapshot == null) { missing.add(new AnatomyMissingData(item.id, item.exerciseVersionId, "PUBLISHED_EXERCISE_SNAPSHOT_UNAVAILABLE")); continue; }
            snapshot.movementPatterns().stream().sorted().forEach(pattern -> patterns.computeIfAbsent(pattern, ignored -> new ArrayList<>()).add(item.id));
            for (ItemAnatomyContribution contribution : snapshot.contributions()) {
                if (!contribution.allocation()) continue;
                if (contribution.anatomicalStructureId() == null || contribution.loadChannel() == null) continue;
                String laterality = laterality(contribution.sideRule(), item.dose == null ? null : item.dose.side);
                if ("UNSPECIFIED".equals(laterality)) lateralityFindings.add(new AnatomyFinding("ANATOMY_LATERALITY_CONFLICT",
                        "Contribution laterality is dynamic but the item dose does not specify a side.", List.of(item.id)));
                var breakdown = new AnatomyContributionBreakdown(item.id, snapshot.sourceExerciseVersionId(), contribution.id(), contribution.role(),
                        contribution.coefficientLow(), contribution.coefficientHigh(), laterality, contribution.confidenceClass(), contribution.evidenceGrade(), contribution.evidence());
                grouped.computeIfAbsent(contribution.loadChannel(), ignored -> new TreeMap<>(Comparator.comparing(StructureSideKey::stableKey)))
                        .computeIfAbsent(new StructureSideKey(contribution.anatomicalStructureId(), breakdown.laterality()), ignored -> new ArrayList<>()).add(breakdown);
                aggregateCandidates.addAll(targets(item.id, snapshot.sourceExerciseVersionId(), contribution, breakdown.laterality()));
                visualCandidates.addAll(visualTargets(item.id, snapshot.sourceExerciseVersionId(), contribution, breakdown.laterality()));
            }
        }
        var direct = new ArrayList<AnatomyStructureExposure>();
        var channels = new ArrayList<AnatomyChannel>();
        for (var channel : grouped.entrySet()) {
            var exposures = new ArrayList<AnatomyStructureExposure>();
            for (var structure : channel.getValue().entrySet()) {
                BigDecimal low = structure.getValue().stream().map(AnatomyContributionBreakdown::coefficientLow).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal high = structure.getValue().stream().map(AnatomyContributionBreakdown::coefficientHigh).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                var first = structure.getValue().get(0);
                var source = snapshots.get(first.itemId()).contributions().stream().filter(c -> c.id().equals(first.contributionId())).findFirst().orElseThrow();
                var exposure = new AnatomyStructureExposure(structure.getKey().structureId(), source.anatomicalStructureCode(), source.anatomicalStructureType(), channel.getKey(), structure.getKey().laterality(), low, high, List.copyOf(structure.getValue()));
                exposures.add(exposure); direct.add(exposure);
            }
            channels.add(new AnatomyChannel(channel.getKey(), List.copyOf(exposures)));
        }
        var movementPatterns = patterns.entrySet().stream().map(e -> new AnatomyMovementPattern(e.getKey(), List.copyOf(e.getValue()))).toList();
        AnatomyCompleteness completeness = version.items.isEmpty() ? AnatomyCompleteness.UNAVAILABLE : missing.isEmpty() ? AnatomyCompleteness.COMPLETE : AnatomyCompleteness.PARTIAL;
        var findings = new ArrayList<AnatomyFinding>(lateralityFindings);
        if (!missing.isEmpty()) findings.add(new AnatomyFinding("ANATOMY_SNAPSHOT_INCOMPLETE", "One or more item-level published exercise snapshots are unavailable.", missing.stream().map(AnatomyMissingData::itemId).toList()));
        var unmapped = unmappedStructures(version, snapshots);
        return new AnatomyAnalysisView("exercise-set-anatomy-policy-v1", version.version, null, draft, !draft, completeness,
                List.copyOf(channels), List.copyOf(direct), movementPatterns, List.copyOf(findings), List.copyOf(missing),
                aggregate(aggregateCandidates), mappingCompleteness(version, snapshots), unmapped, unmapped.size(),
                visualMappingVersion(snapshots), mappingCompleteness(version, snapshots), VISUAL_CONCENTRATION_POLICY_VERSION,
                visualExposures(visualCandidates, mappingCompleteness(version, snapshots)));
    }

    /** Uses only allocation/direct contributions. Aggregated hierarchy output is intentionally never re-projected. */
    private static List<VisualRegionExposure> visualExposures(List<VisualCandidate> candidates, AnatomyMappingCompleteness completeness) {
        var deduplicated = new TreeMap<String, VisualCandidate>();
        for (var candidate : candidates) deduplicated.putIfAbsent(candidate.deduplicationKey(), candidate);
        var grouped = new TreeMap<String, List<VisualCandidate>>();
        for (var candidate : deduplicated.values()) grouped.computeIfAbsent(candidate.groupKey(), ignored -> new ArrayList<>()).add(candidate);
        var totals = new EnumMap<VisualRegionChannel, BigDecimal>(VisualRegionChannel.class);
        for (var group : grouped.values()) {
            var channel = group.getFirst().channel();
            totals.merge(channel, rawValue(group), BigDecimal::add);
        }
        var result = new ArrayList<VisualRegionExposure>();
        for (var group : grouped.values()) {
            var first = group.getFirst();
            BigDecimal raw = rawValue(group);
            BigDecimal low = group.stream().map(c -> c.contribution().coefficientLow()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal high = group.stream().map(c -> c.contribution().coefficientHigh()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal denominator = totals.getOrDefault(first.channel(), BigDecimal.ZERO);
            BigDecimal share = denominator.signum() == 0 ? BigDecimal.ZERO : raw.divide(denominator, 8, java.math.RoundingMode.HALF_UP);
            var sources = new TreeMap<String, VisualRegionStructureReference>();
            var breakdowns = new ArrayList<VisualRegionBreakdown>();
            for (var candidate : group) {
                var source = candidate.contribution();
                sources.putIfAbsent(source.anatomicalStructureId().toString(), new VisualRegionStructureReference(source.anatomicalStructureId(), source.anatomicalStructureCode(), source.anatomicalStructureType()));
                breakdowns.add(new VisualRegionBreakdown(candidate.itemId(), candidate.exerciseVersionId(), source.id(), source.anatomicalStructureId(),
                        source.anatomicalStructureCode(), coefficientHigh(source), source.coefficientLow(), source.coefficientHigh(), source.role(), source.evidence()));
            }
            result.add(new VisualRegionExposure(first.region().code(), first.view(), first.layer(), first.laterality(), first.channel(), first.mappingVersion(), raw,
                    "COEFFICIENT_HIGH_SUM", share, concentrationBand(share), completeness, low, high,
                    List.copyOf(sources.values()), List.copyOf(breakdowns)));
        }
        return List.copyOf(result);
    }

    private static BigDecimal rawValue(List<VisualCandidate> candidates) { return candidates.stream().map(c -> coefficientHigh(c.contribution())).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static BigDecimal coefficientHigh(ItemAnatomyContribution contribution) { return contribution.coefficientHigh() == null ? BigDecimal.ZERO : contribution.coefficientHigh(); }
    private static ConcentrationBand concentrationBand(BigDecimal share) {
        if (share.signum() <= 0) return ConcentrationBand.NO_DATA;
        if (share.compareTo(new BigDecimal("0.25")) < 0) return ConcentrationBand.LOW;
        if (share.compareTo(new BigDecimal("0.60")) < 0) return ConcentrationBand.SIGNIFICANT;
        return ConcentrationBand.DOMINANT;
    }

    private static List<VisualCandidate> visualTargets(UUID itemId, UUID exerciseVersionId, ItemAnatomyContribution contribution, String sourceLaterality) {
        if (contribution.visualMapping() == null) return List.of();
        var result = new ArrayList<VisualCandidate>();
        for (var region : contribution.visualMapping().regions()) {
            result.add(new VisualCandidate(itemId, exerciseVersionId, contribution, region,
                    VisualRegionView.valueOf(region.viewName()), VisualRegionLayer.valueOf(region.layerName()),
                    visualLaterality(sourceLaterality), VisualRegionChannel.valueOf(contribution.loadChannel())));
        }
        return result;
    }

    private static VisualRegionLaterality visualLaterality(String laterality) {
        return "LEFT".equals(laterality) ? VisualRegionLaterality.LEFT : "RIGHT".equals(laterality) ? VisualRegionLaterality.RIGHT : VisualRegionLaterality.CENTRAL;
    }

    private static String visualMappingVersion(Map<UUID, ItemAnatomySnapshot> snapshots) {
        var versions = snapshots.values().stream().flatMap(snapshot -> snapshot.contributions().stream())
                .map(ItemAnatomyContribution::visualMapping).filter(Objects::nonNull).map(ItemVisualMapping::mappingVersion).distinct().sorted().toList();
        return versions.isEmpty() ? "UNAVAILABLE" : versions.size() == 1 ? versions.getFirst().toString() : "MIXED";
    }

    private static List<AnatomyAggregatedExposure> aggregate(List<AggregateCandidate> candidates) {
        var deduplicated = new HashMap<String, AggregateCandidate>();
        for (var candidate : candidates) {
            String key = candidate.itemId + ":" + candidate.channel + ":" + candidate.laterality + ":" + candidate.contribution.id() + ":" + candidate.target.id();
            deduplicated.merge(key, candidate, (left, right) -> PATH_ORDER.compare(left, right) <= 0 ? left : right);
        }
        var groups = new TreeMap<String, List<AggregateCandidate>>();
        for (var candidate : deduplicated.values()) groups.computeIfAbsent(candidate.groupKey(), ignored -> new ArrayList<>()).add(candidate);
        return groups.values().stream().map(group -> {
            var target = group.getFirst().target;
            var breakdowns = new ArrayList<AnatomyAggregationBreakdown>();
            BigDecimal low = BigDecimal.ZERO, high = BigDecimal.ZERO;
            for (var candidate : group.stream().sorted(PATH_ORDER).toList()) {
                boolean shadowed = group.stream().anyMatch(other -> other != candidate && other.itemId.equals(candidate.itemId)
                        && !other.source.id().equals(candidate.source.id()) && other.pathIds.contains(candidate.source.id()));
                boolean included = !shadowed;
                if (included) {
                    if (candidate.contribution.coefficientLow() != null) low = low.add(candidate.contribution.coefficientLow());
                    if (candidate.contribution.coefficientHigh() != null) high = high.add(candidate.contribution.coefficientHigh());
                }
                breakdowns.add(new AnatomyAggregationBreakdown(candidate.itemId, candidate.exerciseVersionId,
                        candidate.contribution.id(), included, included ? "INCLUDED" : "EXCLUDED_MORE_SPECIFIC_SOURCE",
                        candidate.pathCodes));
            }
            return new AnatomyAggregatedExposure(target.id(), target.code(), target.type(), group.getFirst().channel,
                    group.getFirst().laterality, low, high, List.copyOf(breakdowns));
        }).toList();
    }

    private static List<AggregateCandidate> targets(UUID itemId, UUID exerciseVersionId, ItemAnatomyContribution contribution, String laterality) {
        var source = new ItemAnatomyStructure(contribution.anatomicalStructureId(), contribution.anatomicalStructureCode(), contribution.anatomicalStructureType());
        var result = new ArrayList<AggregateCandidate>();
        result.add(new AggregateCandidate(itemId, exerciseVersionId, contribution.loadChannel(), laterality, contribution, source, source, List.of(source.id()), List.of(source.code())));
        for (var hierarchyPath : contribution.hierarchyPaths()) {
            if (hierarchyPath.steps().isEmpty()) continue;
            var target = hierarchyPath.steps().getLast();
            var path = new ArrayList<ItemAnatomyStructure>(); path.add(source); path.addAll(hierarchyPath.steps());
            result.add(new AggregateCandidate(itemId, exerciseVersionId, contribution.loadChannel(), laterality, contribution, source, target,
                    path.stream().map(ItemAnatomyStructure::id).toList(), path.stream().map(ItemAnatomyStructure::code).toList()));
        }
        return result;
    }

    private static AnatomyMappingCompleteness mappingCompleteness(ExerciseSetEntities.ExerciseSetVersionEntity version, Map<UUID, ItemAnatomySnapshot> snapshots) {
        int contributions = 0, mapped = 0;
        for (var item : version.items) {
            var snapshot = snapshots.get(item.id); if (snapshot == null) continue;
            for (var contribution : snapshot.contributions()) if (contribution.allocation() && contribution.anatomicalStructureId() != null) {
                contributions++; if (contribution.visualMapping() != null && !contribution.visualMapping().regions().isEmpty()) mapped++;
            }
        }
        return contributions == 0 ? AnatomyMappingCompleteness.UNAVAILABLE : mapped == contributions ? AnatomyMappingCompleteness.COMPLETE : AnatomyMappingCompleteness.PARTIAL;
    }

    private static List<AnatomyUnmappedStructure> unmappedStructures(ExerciseSetEntities.ExerciseSetVersionEntity version,
                                                                       Map<UUID, ItemAnatomySnapshot> snapshots) {
        var unmapped = new TreeMap<String, AnatomyUnmappedStructure>();
        for (var item : version.items) {
            var snapshot = snapshots.get(item.id); if (snapshot == null) continue;
            for (var contribution : snapshot.contributions()) if (contribution.allocation() && contribution.anatomicalStructureId() != null
                    && (contribution.visualMapping() == null || contribution.visualMapping().regions().isEmpty())) {
                var structure = new AnatomyUnmappedStructure(contribution.anatomicalStructureId(), contribution.anatomicalStructureCode(), contribution.anatomicalStructureType());
                unmapped.putIfAbsent(contribution.anatomicalStructureCode(), structure);
            }
        }
        return List.copyOf(unmapped.values());
    }
    private static String laterality(String sideRule, Side prescribed) {
        if ("LEFT".equals(sideRule) || "RIGHT".equals(sideRule) || "BILATERAL".equals(sideRule) || "NOT_APPLICABLE".equals(sideRule)) return sideRule;
        return prescribed == null ? "UNSPECIFIED" : prescribed.name();
    }
    record ItemAnatomySnapshot(UUID sourceExerciseVersionId, int sourceExerciseVersionNumber, int sourceProfileSchemaVersion,
                               List<String> movementPatterns, List<ItemAnatomyContribution> contributions) { }
    record ItemAnatomyContribution(UUID id, UUID anatomicalStructureId, String anatomicalStructureCode, String anatomicalStructureType,
                                   String role, String loadChannel, BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                   String confidenceClass, String evidenceGrade, boolean allocation, String variantCondition,
                                   String sideRule, List<AnatomyEvidence> evidence,
                                   List<ItemAnatomyPath> hierarchyPaths, ItemVisualMapping visualMapping) {
        ItemAnatomyContribution {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            hierarchyPaths = hierarchyPaths == null ? List.of() : List.copyOf(hierarchyPaths);
        }
        ItemAnatomyContribution(UUID id, UUID anatomicalStructureId, String anatomicalStructureCode, String anatomicalStructureType,
                                String role, String loadChannel, BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                String confidenceClass, String evidenceGrade, boolean allocation, String variantCondition,
                                String sideRule, List<AnatomyEvidence> evidence) {
            this(id, anatomicalStructureId, anatomicalStructureCode, anatomicalStructureType, role, loadChannel,
                    coefficientLow, coefficientHigh, confidenceClass, evidenceGrade, allocation, variantCondition,
                    sideRule, evidence, List.of(), null);
        }
    }
    record ItemAnatomyPath(List<ItemAnatomyStructure> steps) { public ItemAnatomyPath { steps = List.copyOf(steps); } }
    record ItemAnatomyStructure(UUID id, String code, String type) { }
    record ItemVisualMapping(long mappingVersion, List<ItemVisualRegion> regions) { public ItemVisualMapping { regions = List.copyOf(regions); } }
    record ItemVisualRegion(UUID id, String code, String displayName, String viewName, String layerName,
                            String labelKey, UUID parentRegionId, int displayOrder, String status) { }
    private record StructureSideKey(UUID structureId, String laterality) { String stableKey() { return structureId + ":" + laterality; } }
    private record AggregateCandidate(UUID itemId, UUID exerciseVersionId, String channel, String laterality,
                                      ItemAnatomyContribution contribution, ItemAnatomyStructure source,
                                      ItemAnatomyStructure target, List<UUID> pathIds, List<String> pathCodes) {
        String groupKey() { return channel + ":" + target.id() + ":" + laterality; }
    }
    private record VisualCandidate(UUID itemId, UUID exerciseVersionId, ItemAnatomyContribution contribution,
                                   ItemVisualRegion region, VisualRegionView view, VisualRegionLayer layer,
                                   VisualRegionLaterality laterality, VisualRegionChannel channel) {
        String deduplicationKey() { return itemId + ":" + channel + ":" + laterality + ":" + contribution.id() + ":" + region.code(); }
        long mappingVersion() { return contribution.visualMapping().mappingVersion(); }
        String groupKey() { return region.code() + ":" + view + ":" + layer + ":" + laterality + ":" + channel + ":" + mappingVersion(); }
    }
    private static final Comparator<AggregateCandidate> PATH_ORDER = Comparator.comparingInt((AggregateCandidate item) -> item.pathCodes.size())
            .thenComparing(item -> String.join("/", item.pathCodes));

    private static void requiredPhases(SetProfile profile, Set<Phase> phases, List<AnalysisFinding> findings) {
        if (profile == null) return;
        if (profile == SetProfile.FULL_SELF_GUIDED) { required(phases, Phase.PREPARATION, findings); required(phases, Phase.MAIN, findings); }
        else if (profile == SetProfile.WARMUP_MODULE) required(phases, Phase.PREPARATION, findings);
        else if (profile == SetProfile.MAIN_MODULE) required(phases, Phase.MAIN, findings);
        else if (profile == SetProfile.ACCESSORY_MODULE && !phases.contains(Phase.ACCESSORY)) warn(findings, "PROFILE_PHASE_MISMATCH", FindingCategory.STRUCTURE, "profile", null, List.of());
        else if (profile == SetProfile.COOLDOWN_MODULE && !phases.contains(Phase.COOLDOWN)) warn(findings, "PROFILE_PHASE_MISMATCH", FindingCategory.STRUCTURE, "profile", null, List.of());
    }
    private static void required(Set<Phase> phases, Phase phase, List<AnalysisFinding> findings) { if (!phases.contains(phase)) block(findings, "PROFILE_PHASE_REQUIRED", "phase", phase, List.of()); }
    private static void duplicateRules(List<ExerciseSetEntities.ExerciseSetItemEntity> items, List<AnalysisFinding> findings) {
        for (int i=1;i<items.size();i++) if (items.get(i-1).exerciseVersionId != null && items.get(i-1).exerciseVersionId.equals(items.get(i).exerciseVersionId))
            warn(findings, "CONSECUTIVE_DUPLICATE_EXERCISE", FindingCategory.DUPLICATE, "exerciseVersionId", items.get(i).phase, List.of(items.get(i-1).id,items.get(i).id));
        var seen = new HashMap<UUID, UUID>();
        for (var item:items) { if (item.exerciseVersionId == null) continue; var first=seen.putIfAbsent(item.exerciseVersionId,item.id); if(first != null) warn(findings, "DUPLICATE_EXACT_EXERCISE_VERSION", FindingCategory.DUPLICATE, "exerciseVersionId", item.phase, ids(first, item.id)); }
    }
    private static void equipmentRules(List<ExerciseSetEntities.ExerciseSetItemEntity> items, List<AnalysisFinding> findings) {
        if (transitions(items) > 2) warn(findings, "EQUIPMENT_TRANSITIONS", FindingCategory.EQUIPMENT, "requiredEquipment", null, List.of());
        if (doseSwitches(items) > 2) suggest(findings, "DOSE_KIND_SWITCHING", FindingCategory.STRUCTURE, "dose.type", null, List.of());
    }
    private static TimeResult time(List<ExerciseSetEntities.ExerciseSetItemEntity> items, List<AnalysisFinding> findings) {
        long total=0; boolean partial=false; boolean overflow=false;
        for(var item:items) { try { Long seconds=seconds(item.dose); if(seconds==null) { partial=true; warn(findings,"TIME_ESTIMATE_PARTIAL",FindingCategory.TIME,"dose",item.phase,ids(item)); } else total=Math.addExact(total,seconds); } catch (ArithmeticException e) { overflow=true; } }
        if (items.isEmpty()) return new TimeResult(null, TimeConfidence.UNAVAILABLE);
        if (overflow || total > Integer.MAX_VALUE) { warn(findings,"TIME_ESTIMATE_OVERFLOW",FindingCategory.TIME,"dose",null,List.of()); return new TimeResult(null, TimeConfidence.UNAVAILABLE); }
        if (total == 0) { warn(findings,"TIME_ESTIMATE_UNAVAILABLE",FindingCategory.TIME,"dose",null,List.of()); return new TimeResult(null, TimeConfidence.UNAVAILABLE); }
        return new TimeResult((int) total, partial ? TimeConfidence.PARTIAL : TimeConfidence.COMPLETE);
    }
    private static Long seconds(ExerciseSetEntities.DoseEntity d) {
        if (d == null) return null;
        if (d instanceof ExerciseSetEntities.StrengthDoseEntity) { if(d.sets==null) return null; long reps=d.reps!=null?d.reps:(d.repMin!=null&&d.repMax!=null?((long)d.repMin+d.repMax)/2:0); return Math.multiplyExact(d.sets,Math.addExact(Math.multiplyExact(reps,4),Optional.ofNullable(d.restSeconds).orElse(60))); }
        if (d instanceof ExerciseSetEntities.IsometricDoseEntity) return d.sets==null||d.holdSeconds==null?null:Math.multiplyExact(d.sets,Math.addExact(d.holdSeconds.longValue(), Optional.ofNullable(d.restSeconds).orElse(45).longValue()));
        if (d instanceof ExerciseSetEntities.MobilityDoseEntity) return d.durationSeconds!=null?d.durationSeconds.longValue():(d.reps==null?null:Math.multiplyExact(d.reps,6L));
        if (d instanceof ExerciseSetEntities.StretchDoseEntity) return d.holdSeconds==null||d.repetitions==null?null:Math.multiplyExact(d.holdSeconds,d.repetitions.longValue());
        if (d instanceof ExerciseSetEntities.BreathingDoseEntity) return d.durationSeconds!=null?d.durationSeconds.longValue():(d.cycles==null?null:Math.multiplyExact(d.cycles,6L));
        if (d instanceof ExerciseSetEntities.AerobicDoseEntity) return d.durationSeconds == null ? null : d.durationSeconds.longValue();
        return null;
    }
    private static int transitions(List<ExerciseSetEntities.ExerciseSetItemEntity> items) { int result=0; String previous=null; for(var i:items){String current=i.requiredEquipment; if(previous!=null&&!Objects.equals(previous,current))result++; previous=current;} return result; }
    private static int doseSwitches(List<ExerciseSetEntities.ExerciseSetItemEntity> items) { int result=0; Class<?> previous=null; for(var i:items){Class<?> current=i.dose == null ? null : i.dose.getClass();if(previous!=null&&current!=null&&!previous.equals(current))result++;previous=current;}return result; }
    private static void block(List<AnalysisFinding> fs,String code,String field,Phase phase,List<UUID> ids){suggest(fs,code,FindingCategory.STRUCTURE,field,phase,ids);}
    private static void warn(List<AnalysisFinding> fs,String code,FindingCategory category,String field,Phase phase,List<UUID> ids){suggest(fs,code,category,field,phase,ids);}
    private static void suggest(List<AnalysisFinding> fs,String code,String field,Phase phase,List<UUID> ids){suggest(fs,code,FindingCategory.STRUCTURE,field,phase,ids);}
    private static void suggest(List<AnalysisFinding> fs,String code,FindingCategory category,String field,Phase phase,List<UUID> ids){fs.add(new AnalysisFinding(code,RULE_VERSION,FindingSeverity.SUGGESTION,category,"exercise-set.analysis."+code.toLowerCase(),"Consider reviewing this part of the set.",ids,phase,field,"review",false));}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static List<UUID> ids(ExerciseSetEntities.ExerciseSetItemEntity item) { return item.id == null ? List.of() : List.of(item.id); }
    private static List<UUID> ids(UUID first, UUID second) { return first == null || second == null ? List.of() : List.of(first, second); }
    private record TimeResult(Integer seconds, TimeConfidence confidence) { }
}
