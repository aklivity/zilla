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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;
import io.aklivity.zilla.manager.internal.commands.install.ZpmRepository;

public final class ZpmCache
{
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public ZpmCache(
        List<ZpmRepository> repositoriesConfig,
        Path directory)
    {

        // Set up RepositorySystem and session
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        this.repositorySystem = locator.getService(RepositorySystem.class);
        this.session = newSession(repositorySystem, directory);

        // Map ZpmRepository to RemoteRepository for Maven Resolver
        this.repositories = repositoriesConfig.stream()
            .map(this::toRemoteRepository)
            .collect(Collectors.toList());
    }

    public List<ZpmArtifact> resolve(
        List<ZpmDependency> imports,
        List<ZpmDependency> dependencies)
    {

        // Resolve imported dependencies and collect versions
        Map<ZpmDependency, String> imported = resolveImports(imports);

        // Build CollectRequest for dependencies
        CollectRequest collectRequest = createCollectRequest(imported, dependencies);

        return resolveDependencyArtifacts(collectRequest);
    }

    private Map<ZpmDependency, String> resolveImports(
        List<ZpmDependency> imports)
    {
        Map<ZpmDependency, String> imported = new LinkedHashMap<>();

        if (imports != null)
        {
            CollectRequest collectRequest = createCollectRequest(emptyMap(), imports);
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

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

    private CollectRequest createCollectRequest(
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

        return collectRequest;
    }

    private List<ZpmArtifact> resolveDependencyArtifacts(
        CollectRequest collectRequest)
    {
        List<ZpmArtifact> artifacts = new LinkedList<>();
        try
        {
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
            DependencyResult result = repositorySystem.resolveDependencies(session, dependencyRequest);

            for (ArtifactResult artifactResult : result.getArtifactResults())
            {
                Artifact artifact = artifactResult.getArtifact();
                Set<ZpmArtifactId> depends = collectDependenciesFromNode(result.getRoot());

                ZpmArtifactId id = new ZpmArtifactId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                Path localPath = artifact.getFile().toPath();
                artifacts.add(new ZpmArtifact(id, localPath, depends));
            }
        }
        catch (DependencyResolutionException e)
        {
            throw new RuntimeException("Failed to resolve dependencies", e);
        }

        return artifacts;
    }

    private Set<ZpmArtifactId> collectDependenciesFromNode(
        DependencyNode rootNode)
    {
        Set<ZpmArtifactId> depends = new LinkedHashSet<>();
        collectDependenciesRecursive(rootNode, depends);
        return depends;
    }

    private void collectDependenciesRecursive(
        DependencyNode node,
        Set<ZpmArtifactId> depends)
    {
        if (node.getDependency() != null && node.getDependency().getArtifact() != null)
        {
            Artifact artifact = node.getDependency().getArtifact();
            ZpmArtifactId dependencyId =
                new ZpmArtifactId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            depends.add(dependencyId);
        }

        // Recurse for each child node
        for (DependencyNode child : node.getChildren())
        {
            collectDependenciesRecursive(child, depends);
        }
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
