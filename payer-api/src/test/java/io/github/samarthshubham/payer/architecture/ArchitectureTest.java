package io.github.samarthshubham.payer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.samarthshubham.payer",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule fhirTypesOnlyInInteropMapperAndService =
      noClasses()
          .that()
          .resideOutsideOfPackages("..payer.interop..", "..payer.mapper..", "..payer.service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.hl7.fhir..");

  @ArchTest
  static final ArchRule controllersAndDtosDoNotDependOnFhirTypes =
      noClasses()
          .that()
          .resideInAnyPackage("..payer.controller..", "..payer.dto..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.hl7.fhir..")
          .allowEmptyShould(true);
}
