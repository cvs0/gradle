/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.workspace.WorkspaceProvider;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Closeable;

@NotThreadSafe
public class ImmutableTransformWorkspaceServices implements TransformWorkspaceServices, Closeable {
    private final WorkspaceProvider workspaceProvider;
    private final CrossBuildInMemoryCache<UnitOfWork.Identity, Try<TransformExecutionResult>> identityCache;

    public ImmutableTransformWorkspaceServices(
        WorkspaceProvider workspaceProvider,
        CrossBuildInMemoryCache<UnitOfWork.Identity, Try<TransformExecutionResult>> identityCache
    ) {
        this.workspaceProvider = workspaceProvider;
        this.identityCache = identityCache;
    }

    @Override
    public WorkspaceProvider getWorkspaceProvider() {
        return workspaceProvider;
    }

    @Override
    public CrossBuildInMemoryCache<UnitOfWork.Identity, Try<TransformExecutionResult>> getIdentityCache() {
        return identityCache;
    }

    @Override
    public void close() {
        workspaceProvider.close();
    }
}
