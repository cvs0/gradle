/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.changes.ChangeDetectorVisitor;
import org.gradle.internal.execution.history.changes.OutputFileChanges;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import javax.annotation.Nullable;

public class HandleExecutionStateStep<C extends IdentityContext, R extends AfterExecutionResult> implements Step<C, R> {
    private final Step<? super PreviousExecutionContext, ? extends R> delegate;

    public HandleExecutionStateStep(Step<? super PreviousExecutionContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        Identity identity = context.getIdentity();
        PreviousExecutionState previousExecutionState = work.getWorkspaceProvider().getHistory()
            .flatMap(history -> history.load(identity.getUniqueId()))
            .orElse(null);
        R result = delegate.execute(work, new PreviousExecutionContext(context, previousExecutionState));

        work.getWorkspaceProvider().getHistory()
            .ifPresent(history -> result.getAfterExecutionState()
                // TODO Use Optional.ifPresentOrElse() once available
                .map(afterExecutionState -> {
                    // TODO Make the success of the execution available via the after execution state
                    boolean shouldStore = shouldStore(previousExecutionState, afterExecutionState, result.getExecution().isSuccessful());

                    if (shouldStore) {
                        history.store(
                            context.getIdentity().getUniqueId(),
                            result.getExecution().isSuccessful(),
                            afterExecutionState
                        );
                    }
                    return afterExecutionState;
                })
                .orElseGet(() -> {
                    // If we did not capture any outputs after execution, remove them from history
                    history.remove(context.getIdentity().getUniqueId());
                    return null;
                })
            );
        return result;
    }

    private static boolean shouldStore(@Nullable PreviousExecutionState previousExecutionState, AfterExecutionState afterExecutionState, boolean successful) {
        // We do not store the history if there was a failure and the outputs did not change, since then the next execution can be incremental.
        // For example the current execution fails because of a compilation failure and for the next execution the source file is fixed,
        // so only the one changed source file needs to be compiled.
        // If there is no previous state, then we do have output changes
        if (successful) {
            return true;
        }
        if (previousExecutionState == null) {
            return true;
        }
        return didOutputsChange(
            previousExecutionState.getOutputFilesProducedByWork(),
            afterExecutionState.getOutputFilesProducedByWork());
    }

    private static boolean didOutputsChange(ImmutableSortedMap<String, FileSystemSnapshot> previous, ImmutableSortedMap<String, FileSystemSnapshot> current) {
        // If there are different output properties compared to the previous execution, then we do have output changes
        if (!previous.keySet().equals(current.keySet())) {
            return true;
        }

        // Otherwise, do deep compare of outputs
        ChangeDetectorVisitor visitor = new ChangeDetectorVisitor();
        OutputFileChanges changes = new OutputFileChanges(previous, current);
        changes.accept(visitor);
        return visitor.hasAnyChanges();
    }
}
