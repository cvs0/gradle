/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.api.problems.Problems;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.impl.DefaultExecutionEngine;
import org.gradle.internal.execution.steps.AssignWorkspaceStep;
import org.gradle.internal.execution.steps.CachingResult;
import org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep;
import org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep;
import org.gradle.internal.execution.steps.ChangeOutputsStep;
import org.gradle.internal.execution.steps.ExecuteStep;
import org.gradle.internal.execution.steps.HandleExecutionStateStep;
import org.gradle.internal.execution.steps.IdentifyStep;
import org.gradle.internal.execution.steps.IdentityCacheStep;
import org.gradle.internal.execution.steps.MutableWorkspaceContext;
import org.gradle.internal.execution.steps.MutateWorkspaceStep;
import org.gradle.internal.execution.steps.PreCreateOutputParentsStep;
import org.gradle.internal.execution.steps.RemovePreviousOutputsStep;
import org.gradle.internal.execution.steps.ResolveCachingStateStep;
import org.gradle.internal.execution.steps.ResolveChangesStep;
import org.gradle.internal.execution.steps.ResolveInputChangesStep;
import org.gradle.internal.execution.steps.SkipUpToDateStep;
import org.gradle.internal.execution.steps.Step;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.execution.steps.WorkDeterminedContext;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.vfs.VirtualFileSystem;

/**
 * Note that this is kept as a Java source file as a workaround for IntelliJ editing
 * being very slow with deeply nested constructor calls in Groovy source files.
 */
public class TestExecutionEngineFactory {
    public static ExecutionEngine createExecutionEngine(
        UniqueId buildId,

        BuildCacheController buildCacheController,
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classloaderHierarchyHasher,
        Deleter deleter,
        ExecutionStateChangeDetector changeDetector,
        OutputChangeListener outputChangeListener,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        Problems problems,
        ValidateStep.ValidationWarningRecorder validationWarningReporter,
        VirtualFileSystem virtualFileSystem
    ) {
        // @formatter:off
        return new DefaultExecutionEngine(problems,
            new IdentifyStep<>(buildOperationExecutor,
            new IdentityCacheStep<>(
            new HandleExecutionStateStep<>(
            new AssignWorkspaceStep<>(
            new MutateWorkspaceStep<>(
            new AlwaysExecuteWorkStep<>(
            new CaptureStateBeforeExecutionStep<>(buildOperationExecutor, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
            new ValidateStep<>(virtualFileSystem, validationWarningReporter,
            new ResolveCachingStateStep<>(buildCacheController, false,
            new ResolveChangesStep<>(changeDetector,
            new SkipUpToDateStep<>(
            new ResolveInputChangesStep<>(
            new CaptureStateAfterExecutionStep<>(buildOperationExecutor, buildId, outputSnapshotter,
            new ChangeOutputsStep<>(outputChangeListener,
            new PreCreateOutputParentsStep<>(
            new RemovePreviousOutputsStep<>(deleter, outputChangeListener,
            new ExecuteStep<>(buildOperationExecutor
        ))))))))))))))))));
        // @formatter:on
    }

    private static class AlwaysExecuteWorkStep<C extends MutableWorkspaceContext> implements Step<C, CachingResult> {
        private final Step<? super WorkDeterminedContext, ? extends CachingResult> delegate;

        public AlwaysExecuteWorkStep(Step<? super WorkDeterminedContext, ? extends CachingResult> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CachingResult execute(UnitOfWork work, C context) {
            return delegate.execute(work, new WorkDeterminedContext(context, work));
        }
    }
}
