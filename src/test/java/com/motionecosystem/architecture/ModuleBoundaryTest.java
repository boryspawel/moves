package com.motionecosystem.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    @Test
    void topLevelModulesAreFreeOfCycles() {
        JavaClasses productionClasses = new ClassFileImporter()
                .importPackages("com.motionecosystem");

        slices().matching("com.motionecosystem.(*)..")
                .should().beFreeOfCycles()
                .check(productionClasses);
    }
}
