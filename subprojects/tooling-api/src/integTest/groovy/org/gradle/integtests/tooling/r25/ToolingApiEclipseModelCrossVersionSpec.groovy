/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "export classpath entry option is reflected in eclipse model"() {

        projectDir.file('settings.gradle').text = '''
include 'a'
rootProject.name = 'root'
'''

        projectDir.file('build.gradle').text = '''

apply plugin: 'java'
apply plugin: 'eclipse'

repositories {
    jcenter()
}

configurations {
    provided
}

dependencies {
    compile project(':a')
    compile 'com.google.guava:guava:17.0'
    provided 'org.slf4j:slf4j-log4j12:1.7.12'
}

eclipse {
    classpath {
        plusConfigurations += [ configurations.provided ]
    }
}

configure(project(':a')){
    apply plugin:'java'
}'''

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        rootProject.projectDependencies.find {it.targetProject.name == "a"}.exported ==false
        rootProject.classpath.find { it.file.name.contains("guava") }.exported == false
        rootProject.classpath.find { it.file.name.contains("slf4j-log4j") }.exported == false
    }

    @TargetGradleVersion(">=2.5")
    def "transitive dependencies are listed as direct dependencies in the eclipse model"() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"));
        mavenRepo.module('someGroup', 'someArtifact', '17.0').publish()
        mavenRepo.module('someGroup', 'someArtifact', '16.0.1').publish()

        projectDir.file('settings.gradle').text = '''
include 'a', 'b', 'c'
rootProject.name = 'root'
'''

        projectDir.file('build.gradle').text = """

subprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}


configure(project(':a')) {
    dependencies {
        compile 'someGroup:someArtifact:17.0'
        compile project(':b')
    }
}


configure(project(':b')) {
    dependencies {
        compile project(':c')
    }
}

configure(project(':c')) {
    dependencies {
        compile 'someGroup:someArtifact:16.0.1'
    }
}
"""

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        EclipseProject aProject = rootProject.children.find { it.name == 'a'}
        EclipseProject bProject = rootProject.children.find { it.name == 'b'}
        EclipseProject cProject = rootProject.children.find { it.name == 'c'}
        then:
        aProject.classpath.find { it.file.name == "someArtifact-17.0.jar" }
        bProject.classpath.find { it.file.name == "someArtifact-16.0.1.jar" }
        cProject.classpath.find { it.file.name == "someArtifact-16.0.1.jar" }
    }
}
