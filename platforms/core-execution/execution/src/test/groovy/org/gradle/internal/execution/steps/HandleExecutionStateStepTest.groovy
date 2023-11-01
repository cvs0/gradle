/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.workspace.WorkspaceProvider
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.impl.ImplementationSnapshot

import javax.annotation.Nullable

class HandleExecutionStateStepTest extends StepSpec<MutableWorkspaceContext> implements SnapshotterFixture {
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    def step = new HandleExecutionStateStep(delegate)
    def uniqueId = "test"
    def identity = Stub(UnitOfWork.Identity) {
        getUniqueId() >> uniqueId
    }
    def previousExecutionState = Mock(PreviousExecutionState)

    def originMetadata = Mock(OriginMetadata)
    def beforeExecutionState = Stub(BeforeExecutionState) {
        getImplementation() >> ImplementationSnapshot.of("Test", TestHashCodes.hashCodeFrom(123))
        getAdditionalImplementations() >> ImmutableList.of()
        getInputProperties() >> ImmutableSortedMap.of()
        getInputFileProperties() >> ImmutableSortedMap.of()
    }

    def outputFile = file("output.txt").text = "output"
    def outputFilesProducedByWork = snapshotsOf(output: outputFile)
    def afterExecutionState = Stub(AfterExecutionState) {
        getOutputFilesProducedByWork() >> outputFilesProducedByWork
        getOriginMetadata() >> originMetadata
    }

    def delegateResult = Mock(AfterExecutionResult)

    def setup() {
        _ * context.identity >> identity
        _ * work.workspaceProvider >> Stub(WorkspaceProvider) {
            history >> Optional.of(executionHistoryStore)
        }
    }

    def "output snapshots are removed when none was captured"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        interaction { expectExecute(previousExecutionState) }

        then:
        1 * delegateResult.afterExecutionState >> Optional.empty()
        1 * executionHistoryStore.remove(identity.uniqueId)
        0 * _
    }

    def "output snapshots are stored after successful execution"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        interaction { expectExecute(previousExecutionState) }

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(afterExecutionState)
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.execution >> Try.successful(Mock(ExecutionEngine.Execution))

        then:
        interaction { expectStore(true, outputFilesProducedByWork) }
        0 * _
    }

    def "output snapshots are stored after failed execution when there's no previous state available"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        interaction { expectExecute() }
        1 * delegateResult.afterExecutionState >> Optional.of(afterExecutionState)
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.execution >> Try.failure(new RuntimeException("execution error"))
        _ * context.previousExecutionState >> Optional.empty()

        then:
        interaction { expectStore(false, outputFilesProducedByWork) }
        0 * _
    }

    def "output snapshots are stored after failed execution with changed outputs"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        interaction { expectExecute(previousExecutionState) }

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(afterExecutionState)
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.execution >> Try.failure(new RuntimeException("execution error"))
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> snapshotsOf([:])

        then:
        interaction { expectStore(false, outputFilesProducedByWork) }
        0 * _
    }

    def "output snapshots are not stored after failed execution with unchanged outputs"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        interaction { expectExecute(previousExecutionState) }

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(afterExecutionState)
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * delegateResult.execution >> Try.failure(new RuntimeException("execution error"))
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> outputFilesProducedByWork
        0 * _
    }

    void expectExecute(@Nullable PreviousExecutionState previousExecutionState = null) {
        1 * executionHistoryStore.load(uniqueId) >> Optional.ofNullable(previousExecutionState)
        1 * delegate.execute(work, _) >> { UnitOfWork work, PreviousExecutionContext previousExecutionContext ->
            assert previousExecutionContext.previousExecutionState.orElse(null) == previousExecutionState
            return delegateResult
        }
    }

    void expectStore(boolean successful, ImmutableSortedMap<String, FileSystemSnapshot> finalOutputs) {
        1 * executionHistoryStore.store(
            identity.uniqueId,
            successful,
            { AfterExecutionState executionState ->
                executionState.outputFilesProducedByWork == finalOutputs
                executionState.originMetadata == originMetadata
            }
        )
    }
}
