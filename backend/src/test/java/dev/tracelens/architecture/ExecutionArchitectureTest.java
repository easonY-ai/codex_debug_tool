package dev.tracelens.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "dev.tracelens", importOptions = ImportOption.DoNotIncludeTests.class)
class ExecutionArchitectureTest {
    @ArchTest
    static final ArchRule executionDomainIsFrameworkIndependent = classes()
            .that().resideInAPackage("..domain.execution..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..",
                    "dev.tracelens.domain.execution..");

    @ArchTest
    static final ArchRule executionDomainDoesNotUseOperationalAuditAnnotation = noClasses()
            .that().resideInAPackage("..domain.execution..")
            .should().dependOnClassesThat().resideInAPackage("..domain.operationaldiagnostics..");

    @ArchTest
    static final ArchRule retiredHookNormalizationContextIsAbsent = noClasses()
            .should().resideInAnyPackage(
                    "..application.hooknormalization..",
                    "..domain.hooknormalization..",
                    "..infrastructure.hooknormalization..",
                    "..interfaces.hooknormalization..");
}
