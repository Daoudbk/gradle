/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.TaskSelector
import org.gradle.tooling.model.gradle.BuildInvocations

@ToolingApiVersion(">=1.12")
class TaskSelectorCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.isEmbedded = false
        settingsFile << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << '''
task t1 << {
    println "t1 in $project.name"
}
'''
        file('b').mkdirs()
        file('b').file('build.gradle').text = '''
task t3 << {
    println "t3 in $project.name"
}
task t2 << {
    println "t2 in $project.name"
}
'''
        file('b/c').mkdirs()
        file('b/c').file('build.gradle').text = '''
task t1 << {
    println "t1 in $project.name"
}
task t2 << {
    println "t2 in $project.name"
}
'''
    }

    @TargetGradleVersion(">=1.8 <=1.11")
    def "no task selectors when running action in older container"() {
        when:
        withConnection { connection -> connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        Exception e = thrown()
        e.cause.message.startsWith('No model of type \'BuildInvocations\' is available in this build.')
    }

    @TargetGradleVersion(">=1.12")
    def "can request task selectors in action"() {
        when:
        Map<String, Set<String>> result = withConnection { connection ->
            connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        result != null
        result.keySet() == ['test', 'a', 'b', 'c'] as Set
        result['test'] == ['t1', 't2', 't3'] as Set
        result['b'] == ['t1', 't2'] as Set
        result['c'].isEmpty()
    }

    @TargetGradleVersion(">=1.12")
    def "build task selectors"() {
        OutputStream baos = new ByteArrayOutputStream()
        when:
        BuildInvocations projectSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run() }
        TaskSelector selector = projectSelectors.taskSelectors.find { it -> it.name == 't1'}
        def result = withBuild { BuildLauncher it ->
            it.standardOutput = baos
            it.forEntryPoints(selector)
        }

        then:
        result.standardOutput.contains('t1 in c')
        !result.standardOutput.contains('t1 in test')
    }

    // TODO retrofit to older version
    @TargetGradleVersion(">=1.12")
    def "can request task selectors for project"() {
        given:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        when:
        def selectors = model.taskSelectors.findAll { TaskSelector it ->
            it.projectDir == projectDir
        }
        then:
        selectors*.name as Set == ['t1', 't2', 't3'] as Set

        // TODO radim: how to return all task from getModel(Class) and project's selectors from getModel(Model, Class)
//        when:
//        selectors = model.taskSelectors.find { TaskSelector it ->
//            it.projectDir == file('b')
//        }
//        then:
//        selectors*.name as Set == ['t1', 't2'] as Set
        // TODO test also null (root project)
    }

    def "can request task selectors from obtained GradleProject model"() {
        when:
        GradleProject result = withConnection { it.getModel(GradleProject.class) }

        then:
        result.path == ':'
        result.getTaskSelectors().find { it.name == 't1' } != null
        result.findByPath(':b').getTaskSelectors().find { it.name == 't1' } != null
        result.findByPath(':b:c').getTaskSelectors().find { it.name == 't1' } == null
        result.getTaskSelectors().find { it.name == 't2' } != null
        result.findByPath(':b').getTaskSelectors().find { it.name == 't2' } != null
        result.findByPath(':b:c').getTaskSelectors().find { it.name == 't2' } == null
        result.getTaskSelectors().find { it.name == 't3' } != null
        result.findByPath(':b').getTaskSelectors().find { it.name == 't3' } == null
        result.findByPath(':b:c').getTaskSelectors().find { it.name == 't3' } == null
    }
}
