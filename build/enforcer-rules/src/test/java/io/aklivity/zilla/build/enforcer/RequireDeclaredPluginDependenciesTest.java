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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.junit.jupiter.api.Test;

public class RequireDeclaredPluginDependenciesTest
{
    private static final String K3PO = "io.aklivity.k3po:k3po-maven-plugin";

    @Test
    public void shouldAcceptWhenPluginDependenciesAreDeclared() throws Exception
    {
        MavenProject engine = project("engine");
        MavenProject spec = project("echo.spec", dependency("engine", "jar"));
        withPlugin(spec, "k3po-maven-plugin", dependency("engine", "jar"), dependency("engine", "test-jar"));

        rule(K3PO).execute(helper(spec, engine, spec));
    }

    @Test
    public void shouldRejectWhenPluginDependencyIsNotDeclared() throws Exception
    {
        MavenProject engine = project("engine");
        MavenProject spec = project("echo.spec");
        withPlugin(spec, "k3po-maven-plugin", dependency("engine", "jar"));

        EnforcerRuleException failure = assertThrows(EnforcerRuleException.class,
            () -> rule(K3PO).execute(helper(spec, engine, spec)));

        assertTrue(failure.getMessage().contains("io.aklivity.zilla:echo.spec"));
        assertTrue(failure.getMessage().contains("io.aklivity.zilla:engine"));
        assertTrue(failure.getMessage().contains(K3PO));
    }

    @Test
    public void shouldAcceptDependencyReachableThroughReactorModules() throws Exception
    {
        MavenProject engine = project("engine");
        MavenProject metricsSpec = project("metrics.spec", dependency("engine", "jar"));
        MavenProject metrics = project("metrics", dependency("metrics.spec", "jar"));
        withPlugin(metrics, "k3po-maven-plugin", dependency("engine", "jar"));

        rule(K3PO).execute(helper(metrics, engine, metricsSpec, metrics));
    }

    @Test
    public void shouldIgnorePluginsThatAreNotConfigured() throws Exception
    {
        MavenProject engine = project("engine");
        MavenProject spec = project("echo.spec");
        withPlugin(spec, "other-maven-plugin", dependency("engine", "jar"));

        rule(K3PO).execute(helper(spec, engine, spec));
    }

    @Test
    public void shouldIgnoreProjectsWithoutBuildPlugins() throws Exception
    {
        MavenProject spec = project("echo.spec");

        rule(K3PO).execute(helper(spec, spec));
    }

    @Test
    public void shouldSkipPomTypeDependenciesInTheClosure() throws Exception
    {
        MavenProject engine = project("engine");
        MavenProject spec = project("echo.spec", dependency("engine", "pom"));
        withPlugin(spec, "k3po-maven-plugin", dependency("engine", "jar"));

        assertThrows(EnforcerRuleException.class, () -> rule(K3PO).execute(helper(spec, engine, spec)));
    }

    @Test
    public void shouldRejectExternalSnapshotPluginDependency() throws Exception
    {
        MavenProject spec = project("echo.spec");
        Dependency external = new Dependency();
        external.setGroupId("org.example");
        external.setArtifactId("lib");
        external.setVersion("2.0-SNAPSHOT");
        withPlugin(spec, "k3po-maven-plugin", external);

        EnforcerRuleException failure = assertThrows(EnforcerRuleException.class,
            () -> rule(K3PO).execute(helper(spec, spec)));

        assertTrue(failure.getMessage().contains("org.example:lib:2.0-SNAPSHOT"));
    }

    @Test
    public void shouldRequirePluginsToBeConfigured()
    {
        MavenProject spec = project("echo.spec");

        assertThrows(EnforcerRuleException.class, () -> rule().execute(helper(spec, spec)));
    }

    @Test
    public void shouldFailWhenSessionCannotBeEvaluated() throws Exception
    {
        EnforcerRuleHelper helper = mock(EnforcerRuleHelper.class);
        when(helper.evaluate("${project}")).thenThrow(new ExpressionEvaluationException("no project"));

        assertThrows(EnforcerRuleException.class, () -> rule(K3PO).execute(helper));
    }

    @Test
    public void shouldNotBeCacheable()
    {
        RequireDeclaredPluginDependencies rule = rule(K3PO);

        assertFalse(rule.isCacheable());
        assertFalse(rule.isResultValid(rule));
        assertEquals("", rule.getCacheId());
    }

    private static RequireDeclaredPluginDependencies rule(
        String... plugins)
    {
        RequireDeclaredPluginDependencies rule = new RequireDeclaredPluginDependencies();
        rule.setPlugins(List.of(plugins));
        return rule;
    }

    private static EnforcerRuleHelper helper(
        MavenProject current,
        MavenProject... reactor) throws Exception
    {
        MavenSession session = mock(MavenSession.class);
        when(session.getAllProjects()).thenReturn(List.of(reactor));
        EnforcerRuleHelper helper = mock(EnforcerRuleHelper.class);
        when(helper.evaluate("${project}")).thenReturn(current);
        when(helper.evaluate("${session}")).thenReturn(session);
        return helper;
    }

    private static MavenProject project(
        String artifactId,
        Dependency... dependencies)
    {
        Model model = new Model();
        model.setGroupId("io.aklivity.zilla");
        model.setArtifactId(artifactId);
        model.setVersion("develop-SNAPSHOT");
        for (Dependency dependency : dependencies)
        {
            model.addDependency(dependency);
        }
        return new MavenProject(model);
    }

    private static Dependency dependency(
        String artifactId,
        String type)
    {
        Dependency dependency = new Dependency();
        dependency.setGroupId("io.aklivity.zilla");
        dependency.setArtifactId(artifactId);
        dependency.setVersion("develop-SNAPSHOT");
        dependency.setType(type);
        return dependency;
    }

    private static void withPlugin(
        MavenProject project,
        String artifactId,
        Dependency... dependencies)
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId("io.aklivity.k3po");
        plugin.setArtifactId(artifactId);
        for (Dependency dependency : dependencies)
        {
            plugin.addDependency(dependency);
        }
        Build build = new Build();
        build.addPlugin(plugin);
        project.getModel().setBuild(build);
    }
}
