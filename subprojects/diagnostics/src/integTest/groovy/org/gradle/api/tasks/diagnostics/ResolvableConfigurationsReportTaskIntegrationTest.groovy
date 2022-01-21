/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Ignore

class ResolvableConfigurationsReportTaskIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "if no configurations present in project, task reports complete absence"() {
        expect:
        succeeds ':resolvableConfigurations'
        reportsCompleteAbsenceOfResolvableConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "if only consumable configurations present, task reports complete absence"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = false
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':resolvableConfigurations'
        reportsCompleteAbsenceOfResolvableConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "if only legacy configuration present, and --all not specified, task produces empty report and prompts for rerun"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':resolvableConfigurations'
        reportsNoProperConfigurations()
        promptsForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "if only legacy configuration present, task reports it if --all flag is set"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':resolvableConfigurations', '--all'

        then:
        outputContainsLinewise """> Task :resolvableConfigurations
--------------------------------------------------
Configuration legacy (l)
--------------------------------------------------
Description = My custom legacy configuration"""

        and:
        hasLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "if single resolvable configuration with no attributes or artifacts present, task reports it"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        outputContainsLinewise """> Task :resolvableConfigurations
--------------------------------------------------
Configuration custom
--------------------------------------------------
Description = My custom configuration
"""
        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "if single resolvable configuration present with attributes, task reports it and them"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        outputContainsLinewise """> Task :resolvableConfigurations
--------------------------------------------------
Configuration custom
--------------------------------------------------
Description = My custom configuration

Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "If multiple resolvable configurations present with attributes, task reports them all, sorted alphabetically"() {
        given:
        buildFile << """
            configurations.create("someConf") {
                description = "My first custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }

            configurations.create("otherConf") {
                description = "My second custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.DOCUMENTATION));
                }
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        outputContainsLinewise """> Task :resolvableConfigurations
--------------------------------------------------
Configuration otherConf
--------------------------------------------------
Description = My second custom configuration

Attributes
    - org.gradle.category = documentation

--------------------------------------------------
Configuration someConf
--------------------------------------------------
Description = My first custom configuration

Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "reports resolvable configurations of a Java Library with module dependencies"() {
        given:
        buildFile << """
            plugins { id 'java-library' }

            ${mavenCentralRepository()}

            dependencies {
                api 'org.apache.commons:commons-lang3:3.5'
                implementation 'org.apache.commons:commons-compress:1.19'
            }
        """

        when:
        succeeds ':resolvableConfigurations'

        then:
        outputContainsLinewise """> Task :resolvableConfigurations
--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - compileOnly
    - implementation

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - implementation
    - runtimeOnly

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - testCompileOnly
    - testImplementation

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - testImplementation
    - testRuntimeOnly
"""

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "reports resolvable configurations of a Java Library with module dependencies if --all flag is set"() {
        given:
        buildFile << """
            plugins { id 'java-library' }

            ${mavenCentralRepository()}

            dependencies {
                api 'org.apache.commons:commons-lang3:3.5'
                implementation 'org.apache.commons:commons-compress:1.19'
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':resolvableConfigurations', '--all'

        then:
        outputContainsLinewise """> Task :resolvableConfigurations
--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration archives (l)
--------------------------------------------------
Description = Configuration for archive artifacts.

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - compileOnly
    - implementation

--------------------------------------------------
Configuration default (l)
--------------------------------------------------
Description = Configuration for default artifacts.

Extended Configurations
    - runtimeElements

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'main'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - implementation
    - runtimeOnly

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api
Extended Configurations
    - testCompileOnly
    - testImplementation

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'test'.

Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Extended Configurations
    - testImplementation
    - testRuntimeOnly
"""

        and:
        hasLegacyLegend()
        doesNotHaveIncubatingLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "specifying a missing config with no configs produces empty report"() {
        expect:
        succeeds ':resolvableConfigurations', '--configuration', 'missing'
        reportsCompleteAbsenceOfResolvableConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "specifying a missing config produces empty report"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }
        """

        expect:
        succeeds ':resolvableConfigurations', '--configuration', 'missing'
        outputContains("There are no resolvable configurations on project 'myLib' named 'missing'.")
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "specifying a missing config with --all and legacy configs available produces empty report and no suggestion"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }

            configurations.create("legacy") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':resolvableConfigurations', '--configuration', 'missing', '--all'
        outputContains("There are no resolvable configurations on project 'myLib' named 'missing'.")
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @Ignore // TODO: remove, the JSON expectation would need to be set for this
    @ToBeFixedForConfigurationCache(because = ":resolvableConfigurations")
    def "can write json report to default file"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            resolvableConfigurations {
                reports.json.required = true
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        def outputFile = file('build/reports/configuration/resolvableConfigurations.json')
        outputFile.assertExists()
        outputFile.assertContents(containsNormalizedString("{ json: 'Yea!  This is full of JSON!' }"))
    }

    def "compatibility rules are printed if present"() {
        given: "A compatibility rule applying to the alphabetically first named attribute in the list"
        buildFile << """
            plugins {
                id 'java'
            }

            def flavor = Attribute.of('flavor', String)

            configurations {
                custom {
                    description = "My custom configuration"
                    canBeResolved = true
                    canBeConsumed = false

                    attributes {
                        attribute(flavor, 'vanilla')
                    }
                }
            }

            class CategoryCompatibilityRule implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    details.compatible()
                }
            }

            dependencies.attributesSchema {
                attribute(flavor) {
                    compatibilityRules.add(CategoryCompatibilityRule)
                }
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        outputContainsLinewise("""Compatibility Rules
        --------------------------------------------------
        Description = The following Attributes have compatibility rules defined.

            - flavor""")

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
    }

    def "disambiguation rules are printed if present"() {
        given: "A disambiguation rule applying to the alphabetically first named attribute in the list"
        buildFile << """
            plugins {
                id 'java'
            }

            def flavor = Attribute.of('flavor', String)

            configurations {
                custom {
                    description = "My custom configuration"
                    canBeResolved = true
                    canBeConsumed = false

                    attributes {
                        attribute(flavor, 'vanilla')
                    }
                }
            }

            class CategorySelectionRule implements AttributeDisambiguationRule<String> {
                void execute(MultipleCandidatesDetails<String> details) {
                    if (details.candidateValues.contains('chocolate')) {
                        details.closestMatch('chocolate')
                    }
                }
            }

            dependencies.attributesSchema {
                attribute(flavor) {
                    disambiguationRules.add(CategorySelectionRule)
                }
            }
        """.stripIndent()

        when:
        succeeds ':resolvableConfigurations'

        then:
        outputContainsLinewise("""Disambiguation Rules
        --------------------------------------------------
        Description = The following Attributes have disambiguation rules defined.

            - flavor""")

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
    }
}
