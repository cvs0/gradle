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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.plugins.ide.internal.tooling.model.DefaultIsolatedGradleProject;
import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static org.gradle.plugins.ide.internal.tooling.ToolingModelBuilderSupport.buildFromTask;
import static org.gradle.util.Path.SEPARATOR;

/**
 * Builds the IsolatedGradleProject that contains information about a project and its tasks.
 */
@NonNullApi
public class IsolatedGradleProjectBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.IsolatedGradleProject");
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        return build(project);
    }

    public static DefaultIsolatedGradleProject build(Project project) {
        String projectIdentityPath = ((ProjectInternal) project).getIdentityPath().getPath();
        DefaultIsolatedGradleProject gradleProject = new DefaultIsolatedGradleProject()
            .setProjectIdentifier(new DefaultProjectIdentifier(project.getRootDir(), project.getPath()))
            .setName(project.getName())
            .setDescription(project.getDescription())
            .setBuildDirectory(project.getLayout().getBuildDirectory().getAsFile().get())
            .setProjectDirectory(project.getProjectDir())
            .setBuildTreePath(projectIdentityPath);

        gradleProject.getBuildScript().setSourceFile(project.getBuildFile());

        List<LaunchableGradleTask> tasks = tasks(gradleProject, (TaskContainerInternal) project.getTasks());
        gradleProject.setTasks(tasks);

        return gradleProject;
    }

    private static List<LaunchableGradleTask> tasks(DefaultIsolatedGradleProject owner, TaskContainerInternal tasks) {
        return tasks.getNames().stream()
            .map(tasks::findByName)
            .filter(Objects::nonNull)
            .map(task -> buildFromTask(new LaunchableGradleTask(), owner.getProjectIdentifier(), task)
                .setBuildTreePath(getBuildTreePath(owner.getBuildTreePath(), task))).collect(toList());
    }

    // TODO: there should be a Path util that handles such concatenations
    private static String getBuildTreePath(String ownerBuildTreePath, Task task) {
        String buildTreePath = SEPARATOR + task.getName();
        if (SEPARATOR.equals(ownerBuildTreePath)) {
            return buildTreePath;
        }
        return ownerBuildTreePath + buildTreePath;
    }

}
