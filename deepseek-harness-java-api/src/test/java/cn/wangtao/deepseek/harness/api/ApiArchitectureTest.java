package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.response.Response;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ApiArchitectureTest {

    @Test
    void apiMustNotDependOnDomainOrInfrastructure() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("cn.xiaofuge.deepseek.harness");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("cn.xiaofuge.deepseek.harness.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "cn.xiaofuge.deepseek.harness.domain..",
                        "cn.xiaofuge.deepseek.harness.infrastructure.."
                )
                .check(classes);
    }

    @Test
    void apiContractsMustUseDtoNaming() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("cn.xiaofuge.deepseek.harness.api");

        ArchRuleDefinition.classes()
                .that().resideInAPackage("cn.xiaofuge.deepseek.harness.api.dto")
                .should().haveSimpleNameEndingWith("DTO")
                .check(classes);
    }

    @Test
    void apiResponseMustOnlyContainWrappers() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("cn.xiaofuge.deepseek.harness.api");

        ArchRuleDefinition.classes()
                .that().resideInAPackage("cn.xiaofuge.deepseek.harness.api.response")
                .should().haveSimpleName("Response")
                .orShould().haveSimpleName("ResponsePage")
                .check(classes);
    }

    @Test
    void apiInterfaceMethodsMustReturnResponseWrapper() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("cn.xiaofuge.deepseek.harness.api");

        ArchRuleDefinition.methods()
                .that().areDeclaredInClassesThat().resideInAPackage("cn.xiaofuge.deepseek.harness.api")
                .should().haveRawReturnType(Response.class)
                .check(classes);
    }

    @Test
    void streamContractsMustReturnSseEmitter() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("cn.xiaofuge.deepseek.harness.api.stream");

        ArchRuleDefinition.methods()
                .that().areDeclaredInClassesThat()
                .resideInAPackage("cn.xiaofuge.deepseek.harness.api.stream")
                .should().haveRawReturnType(SseEmitter.class)
                .check(classes);
    }
}
