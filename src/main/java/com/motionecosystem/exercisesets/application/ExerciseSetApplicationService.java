package com.motionecosystem.exercisesets.application;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.*;
import com.motionecosystem.exercisesets.infrastructure.ExerciseSetService;
import com.motionecosystem.exercisesets.ports.ExerciseSetCommandPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application facade keeps API independent from the Hibernate implementation. */
@Service @RequiredArgsConstructor
public class ExerciseSetApplicationService implements ExerciseSetCommandPort {
    private final ExerciseSetService persistence;
    public SetView create(String s){return persistence.create(s);} public List<SetView> list(String s){return persistence.list(s);} public SetView get(String s,UUID id){return persistence.get(s,id);} public List<VersionSummary> listVersions(String s,UUID id){return persistence.listVersions(s,id);} public VersionView version(String s,UUID a,UUID b){return persistence.version(s,a,b);} public VersionView currentDraft(String s,UUID id){return persistence.currentDraft(s,id);} public VersionView latestPublished(String s,UUID id){return persistence.latestPublished(s,id);} public AnalysisView analysis(String s,UUID a,UUID b){return persistence.analysis(s,a,b);} public AnatomyAnalysisView anatomy(String s,UUID a,UUID b){return persistence.anatomy(s,a,b);} public VersionView updateMetadata(String s,UUID a,UUID b,MetadataRequest c){return persistence.updateMetadata(s,a,b,c);} public VersionView addItem(String s,UUID a,UUID b,ItemRequest c){return persistence.addItem(s,a,b,c);} public VersionView updateItem(String s,UUID a,UUID b,UUID c,ItemRequest d){return persistence.updateItem(s,a,b,c,d);} public VersionView moveItem(String s,UUID a,UUID b,MoveRequest c){return persistence.moveItem(s,a,b,c);} public VersionView removeItem(String s,UUID a,UUID b,UUID c,long d){return persistence.removeItem(s,a,b,c,d);} public VersionView publish(String s,UUID a,UUID b,long c){return persistence.publish(s,a,b,c);} public VersionView nextDraft(String s,UUID a,UUID b){return persistence.nextDraft(s,a,b);} public VersionView variantDraft(String s,UUID a,UUID b,CreateVariantRequest c){return persistence.variantDraft(s,a,b,c);} public VersionView retire(String s,UUID a,UUID b){return persistence.retire(s,a,b);}
}
