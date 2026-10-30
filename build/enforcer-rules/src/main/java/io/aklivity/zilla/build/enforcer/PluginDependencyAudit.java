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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PluginDependencyAudit
{
    private static final String SNAPSHOT = "-SNAPSHOT";

    private final Map<String, Set<String>> declaredByProject;

    PluginDependencyAudit(
        Map<String, Set<String>> declaredByProject)
    {
        this.declaredByProject = declaredByProject;
    }

    List<String> violations(
        String project,
        Collection<Coordinate> pluginDependencies)
    {
        final Set<String> reachable = reachableFrom(project);
        final Set<String> violations = new LinkedHashSet<>();

        for (Coordinate dependency : pluginDependencies)
        {
            final String key = dependency.key();

            if (declaredByProject.containsKey(key))
            {
                if (!key.equals(project) && !reachable.contains(key))
                {
                    violations.add(key + " is not reachable through the declared dependencies");
                }
            }
            else if (dependency.version() != null && dependency.version().endsWith(SNAPSHOT))
            {
                violations.add(key + ":" + dependency.version() + " is an external snapshot outside the reactor");
            }
        }

        return new ArrayList<>(violations);
    }

    private Set<String> reachableFrom(
        String project)
    {
        final Set<String> reachable = new HashSet<>();
        final Deque<String> pending = new ArrayDeque<>(declaredByProject.getOrDefault(project, Set.of()));

        while (!pending.isEmpty())
        {
            final String next = pending.pop();

            if (reachable.add(next))
            {
                pending.addAll(declaredByProject.getOrDefault(next, Set.of()));
            }
        }

        return reachable;
    }
}
