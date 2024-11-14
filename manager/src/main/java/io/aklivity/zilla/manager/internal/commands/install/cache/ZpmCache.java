/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal.commands.install.cache;

import static java.util.Collections.emptyMap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;
import io.aklivity.zilla.manager.internal.commands.install.ZpmRepository;

public final class ZpmCache
{
    @Component
    private RepositorySystem repoSystem;

    private RepositorySystemSession session;

    private final List<RemoteRepository> repositories;

    public ZpmCache(
        List<ZpmRepository> repositoriesConfig,
        Path directory)
    {
        this.session = newSession(repoSystem, directory);

        // Map ZpmRepository to RemoteRepository for Maven Resolver
        this.repositories = repositoriesConfig.stream()
            .map(this::toRemoteRepository)
            .collect(Collectors.toList());
    }

    public List<ZpmArtifact> resolve(
        List<ZpmDependency> imports,
        List<ZpmDependency> dependencies)
    {
        final List<ZpmArtifact> artifacts = new ArrayList<>();
        // Resolve imported dependencies and collect versions
        Map<ZpmDependency, String> imported = resolveImports(imports);

        // Build CollectRequest for dependencies
        DependencyRequest collectRequest = createDependencyRequest(imported, dependencies);

        DependencyResult result;
        try
        {
            result = repositorySystem.resolveDependencies(session, collectRequest);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to resolve dependencies", e);
        }
        DependencyNode root = result.getRoot();
        root.accept(new DependencyVisitor()
        {
            final AtomicInteger level = new AtomicInteger();
            int i = -1;

            @Override
            public boolean visitEnter(
                DependencyNode node)
            {
                StringBuilder sb = new StringBuilder();
                int currentLevel = level.getAndIncrement();
                for (int i = 0; i < currentLevel; i++)
                {
                    sb.append("  ");
                }

                final Dependency dep = node.getDependency();
                if (dep != null)
                {
                    final Artifact artifact = dep.getArtifact();
                    final ZpmArtifactId id =
                        new ZpmArtifactId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                    if (currentLevel != 1)
                    {
                        artifacts.get(i).depends.add(id);
                    }
                    else
                    {
                        System.err.println(sb.toString() + dep);
                        artifacts.add(new ZpmArtifact(id, artifact.getFile().toPath(), new LinkedHashSet<>()));
                        i++;
                    }
                }
                return true;
            }

            @Override
            public boolean visitLeave(
                DependencyNode node)
            {
                level.decrementAndGet();
                return true;
            }
        });

        return artifacts;
    }

    private Map<ZpmDependency, String> resolveImports(
        List<ZpmDependency> imports)
    {
        Map<ZpmDependency, String> imported = new LinkedHashMap<>();

        if (imports != null)
        {
            DependencyRequest dependencyRequest = createDependencyRequest(emptyMap(), imports);

            try
            {
                DependencyResult importResult = repositorySystem.resolveDependencies(session, dependencyRequest);
                collectVersionsFromDependencyResult(importResult, imported);
            }
            catch (DependencyResolutionException e)
            {
                throw new RuntimeException("Failed to resolve imports", e);
            }
        }

        return imported;
    }

    private void collectVersionsFromDependencyResult(
        DependencyResult result,
        Map<ZpmDependency, String> imported)
    {

        for (ArtifactResult artifactResult : result.getArtifactResults())
        {
            Artifact artifact = artifactResult.getArtifact();
            ZpmDependency dependency = ZpmDependency.of(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            imported.put(dependency, artifact.getVersion());
        }
    }

    private DependencyRequest createDependencyRequest(
        Map<ZpmDependency, String> imported,
        List<ZpmDependency> dependencies)
    {

        CollectRequest collectRequest = new CollectRequest();
        dependencies.forEach(dep ->
        {
            String version = dep.version != null ? dep.version : imported.get(dep);
            Artifact artifact = new DefaultArtifact(dep.groupId, dep.artifactId, "jar", version);
            collectRequest.addDependency(new Dependency(artifact, "runtime"));
        });
        repositories.forEach(collectRequest::addRepository);

        return new DependencyRequest(collectRequest, null);
    }

    private RemoteRepository toRemoteRepository(
        ZpmRepository repository)
    {

        return new RemoteRepository.Builder("central", "default", repository.location)
            .setRepositoryManager(true)
            .build();
    }

    private static DefaultRepositorySystemSession newSession(
        RepositorySystem system, Path directory)
    {

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(directory.toFile())));
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));

        return session;
    }
}
