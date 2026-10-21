package io.github.samarthshubham.clinic.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.samarthshubham.clinic",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule controllersAndDtosDoNotDependOnFhirTypes =
      noClasses()
          .that()
          .resideInAnyPackage("..controller..", "..dto..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.hl7.fhir..");
}
