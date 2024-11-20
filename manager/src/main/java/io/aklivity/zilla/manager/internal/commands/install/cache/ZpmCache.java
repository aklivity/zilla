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

import static org.eclipse.aether.util.graph.transformer.ConflictResolver.CONFIG_PROP_VERBOSE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.graph.traverser.StaticDependencyTraverser;
import org.eclipse.aether.util.graph.visitor.NodeListGenerator;
import org.eclipse.aether.util.graph.visitor.PreorderDependencyNodeConsumerVisitor;

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
        this.repositorySystem = SupplierRepositorySystemFactory.newRepositorySystem();
        this.session = newRepositorySystemSession(repositorySystem, directory);

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
        Map<ZpmDependency, String> imported = new HashMap<>();
        imports.forEach(imp ->
        {
            Artifact artifact = new DefaultArtifact(imp.groupId, imp.artifactId, "pom", imp.version);
            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            repositories.forEach(descriptorRequest::addRepository);
            descriptorRequest.setArtifact(artifact);
            try
            {
                ArtifactDescriptorResult descriptorResult = repositorySystem.readArtifactDescriptor(session, descriptorRequest);
                final List<Dependency> managedDependencies = descriptorResult.getManagedDependencies();
                managedDependencies.forEach(dep ->
                {
                    final Artifact managedArtifact = dep.getArtifact();
                    imported.put(ZpmDependency.of(managedArtifact.getGroupId(), managedArtifact.getArtifactId(), null),
                        managedArtifact.getVersion());

                });
            }
            catch (ArtifactDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        });

        CollectRequest collectRequest = new CollectRequest();
        dependencies.forEach(dep ->
        {
            String version = dep.version != null ? dep.version : imported.get(dep);
            Artifact artifact = new DefaultArtifact(dep.groupId, dep.artifactId, "jar", version);
            collectRequest.addDependency(new Dependency(artifact, null));
        });
        repositories.forEach(collectRequest::addRepository);

        DependencyResult result;
        try
        {
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
            result = repositorySystem.resolveDependencies(session, dependencyRequest);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to resolve dependencies", e);
        }
        DependencyNode root = result.getRoot();

        NodeListGenerator nlg = new NodeListGenerator();

        root.accept(new PreorderDependencyNodeConsumerVisitor(nlg));
        List<DependencyNode> nodesWithDependencies = nlg.getNodes();

        nodesWithDependencies.forEach(node ->
        {
            Dependency dep = node.getDependency();
            if (dep != null)
            {
                final Artifact artifact = dep.getArtifact();
                List<DependencyNode> children = node.getChildren();
                final ZpmArtifactId id =
                    new ZpmArtifactId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                final Set<ZpmArtifactId> depends = new LinkedHashSet<>();
                children.forEach(c ->
                {
                    final Artifact cArtifact = c.getArtifact();
                    final ZpmArtifactId cid =
                        new ZpmArtifactId(cArtifact.getGroupId(), cArtifact.getArtifactId(), cArtifact.getVersion());
                    depends.add(cid);
                });
                artifacts.add(new ZpmArtifact(id, artifact.getFile().toPath(), depends));
            }
        });

        return artifacts;
    }

    private RemoteRepository toRemoteRepository(
        ZpmRepository repository)
    {

        return new RemoteRepository.Builder("central", "default", repository.location)
            .setRepositoryManager(true)
            .build();
    }

    public static RepositorySystemSession newRepositorySystemSession(
        RepositorySystem system,
        Path dir)
    {
        return new SessionBuilderSupplier(system)
            .get()
                .withLocalRepositoryBaseDirectories(dir)
                .setRepositoryListener(new ConsoleRepositoryListener())
                .setDependencyTraverser(new StaticDependencyTraverser(true))
                .setTransferListener(new ConsoleTransferListener())
                .setConfigProperty("aether.generator.gpg.enabled", Boolean.TRUE.toString())
                .setConfigProperty("aether.syncContext.named.factory", "noop")
                .setConfigProperty(CONFIG_PROP_VERBOSE, "true")
                .build();
    }
}
