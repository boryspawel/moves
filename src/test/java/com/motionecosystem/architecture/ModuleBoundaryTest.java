package com.motionecosystem.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
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
}
