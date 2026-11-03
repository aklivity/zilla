/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.build.enforcer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

public class RequireDeclaredPluginDependencies implements EnforcerRule
{
    private static final String POM_TYPE = "pom";
    private static final String UNCOVERED_MESSAGE =
        "%s has plugin dependencies that the build cache checksum does not cover, " +
        "declare each as a dependency (any scope) of the module:%n  %s";

    private List<String> plugins;

    public void setPlugins(
        List<String> plugins)
    {
        this.plugins = plugins;
    }

    @Override
    public void execute(
        EnforcerRuleHelper helper) throws EnforcerRuleException
    {
        if (plugins == null || plugins.isEmpty())
        {
            throw new EnforcerRuleException("plugins must name the plugins whose dependencies are excluded from the checksum");
        }

        try
        {
            final MavenProject project = (MavenProject) helper.evaluate("${project}");
            final MavenSession session = (MavenSession) helper.evaluate("${session}");

            final List<String> violations = violations(project, session.getAllProjects());

            if (!violations.isEmpty())
            {
                throw new EnforcerRuleException(String.format(UNCOVERED_MESSAGE,
                    key(project), String.join(String.format("%n  "), violations)));
            }
        }
        catch (ExpressionEvaluationException ex)
        {
            throw new EnforcerRuleException("Unable to evaluate the project and session: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isCacheable()
    {
        return false;
    }

    @Override
    public boolean isResultValid(
        EnforcerRule cachedRule)
    {
        return false;
    }

    @Override
    public String getCacheId()
    {
        return "";
    }

    private List<String> violations(
        MavenProject project,
        List<MavenProject> reactor)
    {
        final Map<String, Set<String>> declaredByProject = new HashMap<>();
        reactor.forEach(candidate -> declaredByProject.put(key(candidate), declared(candidate)));
        declaredByProject.put(key(project), declared(project));

        final PluginDependencyAudit audit = new PluginDependencyAudit(declaredByProject);

        return project.getBuildPlugins().stream()
            .filter(plugin -> plugins.contains(plugin.getGroupId() + ":" + plugin.getArtifactId()))
            .flatMap(plugin -> audit.violations(key(project), coordinates(plugin)).stream()
                .map(violation -> "plugin " + plugin.getGroupId() + ":" + plugin.getArtifactId() + ": " + violation))
            .collect(toList());
    }

    private static Set<String> declared(
        MavenProject project)
    {
        return project.getDependencies().stream()
            .filter(dependency -> !POM_TYPE.equals(dependency.getType()))
            .map(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId())
            .collect(toSet());
    }

    private static List<Coordinate> coordinates(
        Plugin plugin)
    {
        return plugin.getDependencies().stream()
            .map(RequireDeclaredPluginDependencies::coordinate)
            .collect(toList());
    }

    private static Coordinate coordinate(
        Dependency dependency)
    {
        return new Coordinate(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    private static String key(
        MavenProject project)
    {
        return project.getGroupId() + ":" + project.getArtifactId();
    }
}
