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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class PluginDependencyAuditTest
{
    private static final Coordinate ENGINE = new Coordinate("io.aklivity.zilla", "engine", "develop-SNAPSHOT");
    private static final Coordinate ENGINE_SPEC = new Coordinate("io.aklivity.zilla", "engine.spec", "develop-SNAPSHOT");

    @Test
    public void shouldAcceptPluginDependencyDeclaredDirectly()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of(
            "io.aklivity.zilla:echo.spec", Set.of("io.aklivity.zilla:engine"),
            "io.aklivity.zilla:engine", emptySet()));

        assertTrue(audit.violations("io.aklivity.zilla:echo.spec", List.of(ENGINE)).isEmpty());
    }

    @Test
    public void shouldAcceptPluginDependencyReachableTransitively()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of(
            "io.aklivity.zilla:metrics", Set.of("io.aklivity.zilla:metrics.spec"),
            "io.aklivity.zilla:metrics.spec", Set.of("io.aklivity.zilla:engine.spec"),
            "io.aklivity.zilla:engine.spec", emptySet()));

        assertTrue(audit.violations("io.aklivity.zilla:metrics", List.of(ENGINE_SPEC)).isEmpty());
    }

    @Test
    public void shouldRejectPluginDependencyThatIsNotReachable()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of(
            "io.aklivity.zilla:echo.spec", Set.of("io.aklivity.zilla:engine.spec"),
            "io.aklivity.zilla:engine.spec", emptySet(),
            "io.aklivity.zilla:engine", Set.of("io.aklivity.zilla:engine.spec")));

        List<String> violations = audit.violations("io.aklivity.zilla:echo.spec", List.of(ENGINE));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("io.aklivity.zilla:engine"));
        assertTrue(violations.get(0).contains("not reachable"));
    }

    @Test
    public void shouldIgnorePluginDependencyOnTheProjectItself()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of(
            "io.aklivity.zilla:engine", Set.of("io.aklivity.zilla:engine.spec"),
            "io.aklivity.zilla:engine.spec", emptySet()));

        assertTrue(audit.violations("io.aklivity.zilla:engine", List.of(ENGINE, ENGINE_SPEC)).isEmpty());
    }

    @Test
    public void shouldTerminateOnCyclicDeclaredDependencies()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of(
            "io.aklivity.zilla:a", Set.of("io.aklivity.zilla:b"),
            "io.aklivity.zilla:b", Set.of("io.aklivity.zilla:a"),
            "io.aklivity.zilla:engine", emptySet()));

        assertEquals(1, audit.violations("io.aklivity.zilla:a", List.of(ENGINE)).size());
    }

    @Test
    public void shouldIgnoreExternalReleaseDependency()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of("io.aklivity.zilla:echo.spec", emptySet()));

        assertTrue(audit.violations("io.aklivity.zilla:echo.spec",
            List.of(new Coordinate("org.example", "lib", "1.0.0"))).isEmpty());
    }

    @Test
    public void shouldIgnoreExternalDependencyWithoutVersion()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of("io.aklivity.zilla:echo.spec", emptySet()));

        assertTrue(audit.violations("io.aklivity.zilla:echo.spec",
            List.of(new Coordinate("org.example", "lib", null))).isEmpty());
    }

    @Test
    public void shouldRejectExternalSnapshotDependency()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of("io.aklivity.zilla:echo.spec", emptySet()));

        List<String> violations = audit.violations("io.aklivity.zilla:echo.spec",
            List.of(new Coordinate("org.example", "lib", "2.0-SNAPSHOT")));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("org.example:lib:2.0-SNAPSHOT"));
        assertTrue(violations.get(0).contains("external snapshot"));
    }

    @Test
    public void shouldReportEveryViolation()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of(
            "io.aklivity.zilla:echo.spec", emptySet(),
            "io.aklivity.zilla:engine", emptySet(),
            "io.aklivity.zilla:engine.spec", emptySet()));

        assertEquals(2, audit.violations("io.aklivity.zilla:echo.spec", asList(ENGINE, ENGINE_SPEC)).size());
    }

    @Test
    public void shouldAcceptNoPluginDependencies()
    {
        PluginDependencyAudit audit = new PluginDependencyAudit(Map.of("io.aklivity.zilla:echo.spec", emptySet()));

        assertTrue(audit.violations("io.aklivity.zilla:echo.spec", emptyList()).isEmpty());
    }
}
