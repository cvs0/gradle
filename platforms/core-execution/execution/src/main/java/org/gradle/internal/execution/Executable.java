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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.workspace.Workspace;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Optional;

/**
 * The user code to be executed as part of the unit of work.
 */
public interface Executable {
    ExecutionOutput execute(ExecutionRequest executionRequest);

    interface ExecutionRequest {
        /**
         * The workspace to produce outputs into.
         * <p>
         * Note: it's {@code null} for tasks as they don't currently have their own workspace.
         */
        Workspace.WorkspaceLocation getWorkspace();

        /**
         * For work capable of incremental execution this is the object to query per-property changes through;
         * {@link Optional#empty()} for non-incremental-capable work.
         */
        Optional<InputChangesInternal> getInputChanges();

        /**
         * Output snapshots indexed by property from the previous execution;
         * {@link Optional#empty()} is information about a previous execution is not available.
         */
        Optional<ImmutableSortedMap<String, FileSystemSnapshot>> getPreviouslyProducedOutputs();
    }
}
