package com.motionecosystem.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.motionecosystem");

    @Test
    void topLevelModulesAreFreeOfCycles() {
        slices().matching("com.motionecosystem.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }

    @Test
    void trainingDomainDoesNotDependOnSpringJpaOrApiLayers() {
        noClasses().that().resideInAnyPackage(
                        "com.motionecosystem.trainingplanning.domain..",
                        "com.motionecosystem.trainingexecution.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "..api..")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void anatomyDomainIsFrameworkIndependentAndOnlyPublicApiCanBeUsedAcrossModules() {
        noClasses().that().resideInAPackage("com.motionecosystem.anatomyreference.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.motionecosystem.anatomyreference.api..",
                        "com.motionecosystem.anatomyreference.application..",
                        "com.motionecosystem.anatomyreference.infrastructure..")
                .check(productionClasses);

        noClasses().that().resideOutsideOfPackage("com.motionecosystem.anatomyreference..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.anatomyreference.domain..",
                        "com.motionecosystem.anatomyreference.application..",
                        "com.motionecosystem.anatomyreference.infrastructure..")
                .check(productionClasses);

        classes().that().resideInAPackage("com.motionecosystem.anatomyreference.infrastructure..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().notBePublic()
                .check(productionClasses);
    }

    @Test
    void exerciseCatalogIsConsumedOnlyThroughItsPublicSnapshotPort() {
        noClasses().that().resideOutsideOfPackage("com.motionecosystem.exercisecatalog..")
                .should().dependOnClassesThat().resideInAPackage("com.motionecosystem.exercisecatalog")
                .check(productionClasses);

        classes().that().resideInAPackage("com.motionecosystem.exercisecatalog..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().notBePublic()
                .check(productionClasses);
    }

    @Test
    void trainingPlanningIsConsumedOnlyThroughPublicSnapshotPorts() {
        noClasses().that().resideOutsideOfPackage("com.motionecosystem.trainingplanning..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.trainingplanning",
                        "com.motionecosystem.trainingplanning.infrastructure..")
                .check(productionClasses);

        classes().that().resideInAPackage("com.motionecosystem.trainingplanning.infrastructure..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().notBePublic()
                .check(productionClasses);
    }

    @Test
    void loadAnalysisDomainIsPureAndPersistenceEntitiesStayInternal() {
        noClasses().that().resideInAPackage("com.motionecosystem.loadanalysis.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(productionClasses);
        classes().that().resideInAPackage("com.motionecosystem.loadanalysis.infrastructure..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().notBePublic()
                .check(productionClasses);
    }

    @Test
    void safetyRulesArePureAndOtherModulesUseOnlyTheSafetyApi() {
        noClasses().that().resideInAPackage("com.motionecosystem.safety.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.motionecosystem.safety.api..",
                        "com.motionecosystem.loadanalysis..",
                        "com.motionecosystem.trainingplanning..",
                        "com.motionecosystem.anatomyreference..",
                        "com.motionecosystem.specialist..",
                        "com.motionecosystem.consent..")
                .check(productionClasses);

        noClasses().that().resideOutsideOfPackage("com.motionecosystem.safety..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.safety.domain..",
                        "com.motionecosystem.safety")
                .allowEmptyShould(true)
                .check(productionClasses);

        classes().that().resideInAPackage("com.motionecosystem.safety..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().notBePublic()
                .check(productionClasses);
    }

    @Test
    void modulesDoNotUseAnotherModulesInfrastructureOrRepositories() {
        noClasses().that().resideOutsideOfPackages(
                        "com.motionecosystem.trainingplanning..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.trainingplanning.infrastructure..")
                .check(productionClasses);

        noClasses().that().resideOutsideOfPackages(
                        "com.motionecosystem.trainingexecution..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.trainingexecution.infrastructure..")
                .check(productionClasses);

        noClasses().that().resideInAPackage("com.motionecosystem.trainingplanning..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.identityaccess.domain..",
                        "com.motionecosystem.identityaccess.infrastructure..")
                .check(productionClasses);

        noClasses().that().resideInAPackage("com.motionecosystem.trainingexecution..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.motionecosystem.trainingplanning.infrastructure..")
                .check(productionClasses);
    }

    @Test
    void modulesDoNotDependOnJpaRepositoriesOwnedByAnotherModule() {
        classes().should(onlyDependOnJpaRepositoriesInTheirOwnModule())
                .check(productionClasses);

        noClasses().that().haveFullyQualifiedName("com.motionecosystem.specialist.SpecialistClientService")
                .should().dependOnClassesThat().resideInAPackage("com.motionecosystem.participant")
                .check(productionClasses);
    }

    @Test
    void consentInternalsAreConsumedOnlyThroughConsentApi() {
        noClasses().that().resideOutsideOfPackage("com.motionecosystem.consent..")
                .should().dependOnClassesThat().resideInAPackage("com.motionecosystem.consent")
                .check(productionClasses);

        classes().that().resideInAPackage("com.motionecosystem.consent..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().notBePublic()
                .check(productionClasses);

        classes().that().resideInAPackage("com.motionecosystem.consent..")
                .and().areAssignableTo(JpaRepository.class)
                .should().notBePublic()
                .check(productionClasses);
    }

    @Test
    void trainingApplicationAndDomainDoNotComposeSql() {
        noClasses().that().resideInAnyPackage(
                        "com.motionecosystem.trainingplanning.application..",
                        "com.motionecosystem.trainingplanning.domain..",
                        "com.motionecosystem.trainingexecution.application..",
                        "com.motionecosystem.trainingexecution.domain..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "org.springframework.jdbc.core.JdbcTemplate")
                .allowEmptyShould(true)
                .check(productionClasses);

        noClasses().that().haveSimpleNameEndingWith("Service")
                .and().resideInAnyPackage(
                        "com.motionecosystem.trainingplanning..",
                        "com.motionecosystem.trainingexecution..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "org.springframework.jdbc.core.JdbcTemplate")
                .check(productionClasses);
    }

    private static ArchCondition<JavaClass> onlyDependOnJpaRepositoriesInTheirOwnModule() {
        return new ArchCondition<>("only depend on JPA repositories in their own top-level module") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                String sourceModule = topLevelModule(source);
                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!topLevelModule(target).isEmpty()
                            && target.isAssignableTo(JpaRepository.class)
                            && !sourceModule.equals(topLevelModule(target))) {
                        events.add(SimpleConditionEvent.violated(source, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String topLevelModule(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String prefix = "com.motionecosystem.";
        if (!packageName.startsWith(prefix)) {
            return "";
        }
        int separator = packageName.indexOf('.', prefix.length());
        return separator < 0 ? packageName.substring(prefix.length()) : packageName.substring(prefix.length(), separator);
    }
}
