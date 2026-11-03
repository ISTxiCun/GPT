package com.istxicun.regression;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
  @Test
  void commonNeverDependsOnFeatureOrAdapterPackages() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.istxicun.backend");
    noClasses()
        .that()
        .resideInAPackage("com.istxicun.backend.common..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.istxicun.backend.distributed..",
            "com.istxicun.backend.config..",
            "com.istxicun.backend.web..",
            "com.istxicun.backend.security..",
            "com.istxicun.backend.modules..",
            "cn.dev33..")
        .because(
            "shared types must remain reusable independently of concrete features and adapters")
        .check(classes);
  }

  @Test
  void applicationCodeCannotReplaceTheGlobalSessionDaoAtRuntime() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.istxicun.backend");
    noClasses()
        .should()
        .callMethod(
            cn.dev33.satoken.SaManager.class,
            "setSaTokenDao",
            cn.dev33.satoken.dao.SaTokenDao.class)
        .because(
            "session storage is installed by the guarded Spring configuration, never switched by"
                + " application code")
        .check(classes);
    noClasses()
        .that()
        .resideOutsideOfPackage("com.istxicun.backend.security..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(cn.dev33.satoken.dao.SaTokenDao.class)
        .because(
            "application callers use Sa-Token public APIs; only the guarded storage adapter may"
                + " access the DAO")
        .check(classes);
  }

  @Test
  void coreNeverDependsOnHttpServletOrApplicationAdapters() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.istxicun.backend");
    noClasses()
        .that()
        .resideInAnyPackage("com.istxicun.backend.common..", "com.istxicun.backend.distributed..")
        .and()
        .resideOutsideOfPackage("com.istxicun.backend.distributed.web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.http..",
            "org.springframework.web..",
            "jakarta.servlet..",
            "com.istxicun.backend.web..",
            "com.istxicun.backend.config..",
            "com.istxicun.backend.security..")
        .because(
            "HTTP belongs to adapters; task and message callers must reuse the core error contract")
        .check(classes);
  }
}
