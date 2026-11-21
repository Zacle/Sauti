package com.sauti.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Keeps the layered modules layered. Applies to modules that have adopted the
 * domain / application / infrastructure split (auth and the new workspace code in tenant);
 * add a module here when it is restructured.
 */
class LayeredModuleArchitectureTest {
    private static final String[] LAYERED_MODULES = {"auth", "tenant", "audit", "business"};
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.sauti");
    }

    @Test
    void domainCodeDependsOnNothingButItselfAndPersistenceAnnotations() {
        for (var module : LAYERED_MODULES) {
            noClasses().that().resideInAPackage("com.sauti." + module + ".domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.sauti." + module + ".application..",
                            "com.sauti." + module + ".infrastructure..",
                            "org.springframework..",
                            "com.sauti.api..")
                    .because("the domain holds the rules and must not know about use cases, adapters or frameworks")
                    .check(classes);
        }
    }

    @Test
    void useCasesReachAdaptersOnlyThroughPorts() {
        for (var module : LAYERED_MODULES) {
            noClasses().that().resideInAPackage("com.sauti." + module + ".application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.sauti." + module + ".infrastructure..",
                            "com.sauti.api..",
                            "jakarta.servlet..")
                    .because("application code depends on port interfaces, never on their implementations or HTTP")
                    .check(classes);
        }
    }

    @Test
    void noOtherModuleReachesIntoAnotherModulesAdapters() {
        for (var module : LAYERED_MODULES) {
            noClasses().that().resideOutsideOfPackage("com.sauti." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage("com.sauti." + module + ".infrastructure..")
                    .because("adapters are private to their module; other modules use its application services")
                    .check(classes);
        }
    }

    @Test
    void theWorkspaceModuleDoesNotDependOnTheIdentityModule() {
        noClasses().that().resideInAPackage("com.sauti.tenant..")
                .should().dependOnClassesThat().resideInAPackage("com.sauti.auth..")
                .because("identity depends on workspaces, not the other way round; the MemberDirectory port inverts it")
                .check(classes);
    }
}
