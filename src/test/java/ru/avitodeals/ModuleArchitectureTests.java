package ru.avitodeals;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
		packages = "ru.avitodeals",
		importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleArchitectureTests {

	private static final String MODULE_PACKAGE_PREFIX = "ru.avitodeals.";
	private static final Set<String> MODULES = Set.of(
			"shared", "listing", "ingestion", "product", "pricing",
			"scoring", "search", "notification", "api");

	@ArchTest
	static final ArchRule modulesFollowTheApprovedDependencyDag = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("shared").definedBy("ru.avitodeals.shared..")
			.layer("listing").definedBy("ru.avitodeals.listing..")
			.layer("ingestion").definedBy("ru.avitodeals.ingestion..")
			.layer("product").definedBy("ru.avitodeals.product..")
			.layer("pricing").definedBy("ru.avitodeals.pricing..")
			.layer("scoring").definedBy("ru.avitodeals.scoring..")
			.layer("search").definedBy("ru.avitodeals.search..")
			.layer("notification").definedBy("ru.avitodeals.notification..")
			.layer("api").definedBy("ru.avitodeals.api..")
			.whereLayer("shared").mayNotAccessAnyLayer()
			.whereLayer("listing").mayOnlyAccessLayers("shared")
			.whereLayer("ingestion").mayOnlyAccessLayers("listing")
			.whereLayer("product").mayOnlyAccessLayers("listing", "shared")
			.whereLayer("pricing").mayOnlyAccessLayers("product", "listing")
			.whereLayer("scoring").mayOnlyAccessLayers("pricing", "product", "listing")
			.whereLayer("search").mayOnlyAccessLayers("ingestion", "scoring", "shared")
			.whereLayer("notification").mayOnlyAccessLayers("scoring", "shared")
			.whereLayer("api").mayOnlyAccessLayers(
					"shared", "listing", "ingestion", "product", "pricing",
					"scoring", "search", "notification")
			.whereLayer("api").mayNotBeAccessedByAnyLayer()
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule modulesUseOnlyPublicPackagesOfDependencies = classes()
			.that().resideInAnyPackage(
					"..shared..", "..listing..", "..ingestion..", "..product..",
					"..pricing..", "..scoring..", "..search..", "..notification..", "..api..")
			.should(new ArchCondition<>("only depend on public packages of other modules") {
				@Override
				public void check(JavaClass source, ConditionEvents events) {
					String sourceModule = moduleOf(source.getPackageName());
					for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
						String targetPackage = dependency.getTargetClass().getPackageName();
						String targetModule = moduleOf(targetPackage);
						if (targetModule == null || sourceModule.equals(targetModule)
								|| isPublicPackage(targetModule, targetPackage)) {
							continue;
						}
						events.add(SimpleConditionEvent.violated(source,
								source.getName() + " depends on internal package " + targetPackage));
					}
				}
			})
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule domainDoesNotDependOnInfrastructure = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAPackage("..infrastructure..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule controllersDoNotDependOnRepositories = noClasses()
			.that().haveSimpleNameEndingWith("Controller")
			.should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule modulesDoNotDependDirectlyOnAvitoListingSource = noClasses()
			.that().resideInAnyPackage(
					"..shared..", "..listing..", "..ingestion..", "..product..", "..pricing..",
					"..scoring..", "..search..", "..notification..", "..api..")
			.should().dependOnClassesThat().haveSimpleName("AvitoListingSource")
			.allowEmptyShould(true);

	private static String moduleOf(String packageName) {
		if (!packageName.startsWith(MODULE_PACKAGE_PREFIX)) {
			return null;
		}
		String modulePackage = packageName.substring(MODULE_PACKAGE_PREFIX.length());
		return MODULES.stream()
				.filter(module -> modulePackage.equals(module) || modulePackage.startsWith(module + "."))
				.findFirst()
				.orElse(null);
	}

	private static boolean isPublicPackage(String module, String packageName) {
		if (module.equals("shared")) {
			return packageName.equals(MODULE_PACKAGE_PREFIX + "shared")
					|| packageName.equals(MODULE_PACKAGE_PREFIX + "shared.application")
					|| packageName.startsWith(MODULE_PACKAGE_PREFIX + "shared.application.");
		}
		return packageName.equals(MODULE_PACKAGE_PREFIX + module + ".application")
				|| packageName.startsWith(MODULE_PACKAGE_PREFIX + module + ".application.");
	}
}
