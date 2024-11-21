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

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.eclipse.aether.util.graph.transformer.ConflictResolver.CONFIG_PROP_VERBOSE;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.generator.gnupg.GnupgSignatureArtifactGeneratorFactory;
import org.eclipse.aether.generator.gnupg.loaders.GpgAgentPasswordLoader;
import org.eclipse.aether.generator.gnupg.loaders.GpgConfLoader;
import org.eclipse.aether.generator.gnupg.loaders.GpgEnvLoader;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecorator;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecoratorFactory;
import org.eclipse.aether.spi.artifact.generator.ArtifactGeneratorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.transport.jdk.JdkTransporterFactory;
import org.eclipse.aether.util.graph.visitor.NodeListGenerator;
import org.eclipse.aether.util.graph.visitor.PreorderDependencyNodeConsumerVisitor;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;
import io.aklivity.zilla.manager.internal.commands.install.ZpmRepository;

public final class ZpmCache
{
    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession session;

    private final List<RemoteRepository> repositories;
    private final ConsoleLogger logger;

    public ZpmCache(
        List<ZpmRepository> repositoriesConfig,
        Path directory,
        ConsoleLogger logger)
    {
        this.logger = logger;
        this.repositorySystem = ZpmSupplierRepositorySystemFactory.newRepositorySystem();
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
        if (imports != null)
        {
            imports.forEach(imp ->
            {
                Artifact artifact = new DefaultArtifact(imp.groupId, imp.artifactId, "pom", imp.version);
                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                repositories.forEach(descriptorRequest::addRepository);
                descriptorRequest.setArtifact(artifact);
                try
                {
                    ArtifactDescriptorResult descriptorResult =
                        repositorySystem.readArtifactDescriptor(session, descriptorRequest);
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
        }
        CollectRequest collectRequest = new CollectRequest();
        dependencies.forEach(dep ->
        {
            String version = ofNullable(dep.version).orElse(imported.get(dep));
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
        List<DependencyNode> nodesWithDependencies = nlg.getNodesWithDependencies();

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
                if (artifacts.stream().noneMatch(a -> a.id.equalsGroupAndArtifact(id) &&
                    new ComparableVersion(id.version).compareTo(new ComparableVersion(a.id.version)) <= 0))
                {
                    artifacts.add(new ZpmArtifact(id, artifact.getFile().toPath(), depends));
                }

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

    private RepositorySystemSession newRepositorySystemSession(
        RepositorySystem system,
        Path dir)
    {
        return new SessionBuilderSupplier(system)
            .get()
                .withLocalRepositoryBaseDirectories(dir)
                .setRepositoryListener(new ZpmConsoleRepositoryListener())
                .setTransferListener(new ZpmConsoleTransferListener())
                .setConfigProperty("aether.generator.gpg.enabled", Boolean.TRUE.toString())
                .setConfigProperty("aether.syncContext.named.factory", "noop")
                .setConfigProperty(CONFIG_PROP_VERBOSE, "true")
                .build();
    }


    final class ZpmSupplierRepositorySystemFactory
    {
        private ZpmSupplierRepositorySystemFactory()
        {
        }

        public static RepositorySystem newRepositorySystem()
        {
            return new RepositorySystemSupplier()
            {
                @Override
                protected Map<String, ArtifactGeneratorFactory> createArtifactGeneratorFactories()
                {
                    Map<String, ArtifactGeneratorFactory> result = super.createArtifactGeneratorFactories();
                    result.put(
                        GnupgSignatureArtifactGeneratorFactory.NAME,
                        new GnupgSignatureArtifactGeneratorFactory(
                            getArtifactPredicateFactory(), getGnupgSignatureArtifactGeneratorFactoryLoaders()));
                    return result;
                }

                private Map<String, GnupgSignatureArtifactGeneratorFactory.Loader>
                    getGnupgSignatureArtifactGeneratorFactoryLoaders()
                {
                    // order matters
                    LinkedHashMap<String, GnupgSignatureArtifactGeneratorFactory.Loader> loaders = new LinkedHashMap<>();
                    loaders.put(GpgEnvLoader.NAME, new GpgEnvLoader());
                    loaders.put(GpgConfLoader.NAME, new GpgConfLoader());
                    loaders.put(GpgAgentPasswordLoader.NAME, new GpgAgentPasswordLoader());
                    return loaders;
                }

                @Override
                protected Map<String, ArtifactDecoratorFactory> createArtifactDecoratorFactories()
                {
                    Map<String, ArtifactDecoratorFactory> result = super.createArtifactDecoratorFactories();
                    result.put("color", new ArtifactDecoratorFactory()
                    {
                        @Override
                        public ArtifactDecorator newInstance(RepositorySystemSession session)
                        {
                            return artifactDescriptorResult ->
                            {
                                Map<String, String> properties = new HashMap<>(
                                    artifactDescriptorResult.getArtifact().getProperties());
                                properties.put("color", "red");
                                return artifactDescriptorResult.getArtifact().setProperties(properties);
                            };
                        }

                        @Override
                        public float getPriority()
                        {
                            return 0;
                        }
                    });
                    return result;
                }

                @Override
                protected Map<String, TransporterFactory> createTransporterFactories()
                {
                    Map<String, TransporterFactory> result = super.createTransporterFactories();
                    result.put(
                        JdkTransporterFactory.NAME,
                        new JdkTransporterFactory(getChecksumExtractor(), getPathProcessor()));
                    return result;
                }
            }.get();
        }
    }

    class ZpmConsoleTransferListener extends AbstractTransferListener
    {
        private final Map<TransferResource, Long> downloads = new ConcurrentHashMap<>();

        private int lastLength;


        @Override
        public void transferInitiated(
            TransferEvent event)
        {
            requireNonNull(event, "event cannot be null");

            logger.debug(String.format("Downloading: %s%s",
                event.getResource().getRepositoryUrl(), event.getResource().getResourceName()));
        }

        @Override
        public void transferProgressed(
            TransferEvent event)
        {
            requireNonNull(event, "event cannot be null");
            TransferResource resource = event.getResource();
            downloads.put(resource, event.getTransferredBytes());

            StringBuilder buffer = new StringBuilder(64);

            for (Map.Entry<TransferResource, Long> entry : downloads.entrySet())
            {
                long total = entry.getKey().getContentLength();
                long complete = entry.getValue();

                buffer.append(getStatus(complete, total)).append("  ");
            }

            int pad = lastLength - buffer.length();
            lastLength = buffer.length();
            pad(buffer, pad);
            buffer.append('\r');

            logger.debug(buffer.toString());
        }

        private String getStatus(
            long complete,
            long total)
        {
            if (total >= 1024)
            {
                return String.format("%d/%d KB", toKB(complete), toKB(total));
            }
            else if (total >= 0)
            {
                return String.format("%d/%d B", complete, total);
            }
            else if (complete >= 1024)
            {
                return String.format("%d KB", toKB(complete));
            }
            else
            {
                return String.format("%d B", complete);
            }
        }

        private void pad(
            StringBuilder buffer,
            int spaces)
        {
            String block = "                                        ";
            while (spaces > 0)
            {
                int n = Math.min(spaces, block.length());
                buffer.append(block, 0, n);
                spaces -= n;
            }
        }

        @Override
        public void transferSucceeded(
            TransferEvent event)
        {
            requireNonNull(event, "event cannot be null");
            transferCompleted(event);

            TransferResource resource = event.getResource();
            long contentLength = event.getTransferredBytes();
            if (contentLength >= 0)
            {
                String len = contentLength >= 1024 ?
                    String.format("%d KB", toKB(contentLength)) : String.format("%d B", contentLength);

                String throughput = "";
                long duration = System.currentTimeMillis() - resource.getTransferStartTime();
                if (duration > 0)
                {
                    long bytes = contentLength - resource.getResumeOffset();
                    DecimalFormat format = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.ENGLISH));
                    double kbPerSec = (bytes / 1024.0) / (duration / 1000.0);
                    throughput = String.format(" at %s KB/sec", format.format(kbPerSec));
                }
                logger.debug(String.format("Downloaded: %s%s (%s%s)",
                    resource.getRepositoryUrl(), resource.getResourceName(), len, throughput));
            }
        }

        @Override
        public void transferFailed(
            TransferEvent event)
        {
            requireNonNull(event, "event cannot be null");
            transferCompleted(event);
        }

        private void transferCompleted(
            TransferEvent event)
        {
            requireNonNull(event, "event cannot be null");
            downloads.remove(event.getResource());

            StringBuilder buffer = new StringBuilder(64);
            pad(buffer, lastLength);
            buffer.append('\r');
            logger.debug(buffer.toString());
        }

        protected long toKB(
            long bytes)
        {
            return (bytes + 1023) / 1024;
        }
    }

    class ZpmConsoleRepositoryListener extends AbstractRepositoryListener
    {
        public void artifactDescriptorInvalid(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Invalid artifact descriptor for %s: %s",
                event.getArtifact(), event.getException().getMessage()));
        }

        public void artifactDescriptorMissing(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Missing artifact descriptor for %s", event.getArtifact()));
        }

        public void artifactResolved(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Resolved artifact %s from %s", event.getArtifact(), event.getRepository()));
        }

        public void artifactDownloading(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Downloading artifact %s from %s", event.getArtifact(), event.getRepository()));
        }

        public void artifactDownloaded(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Downloaded artifact %s from %s", event.getArtifact(), event.getRepository()));
        }

        public void artifactResolving(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Resolving artifact %s", event.getArtifact()));
        }

        public void metadataInvalid(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Invalid metadata %s", event.getMetadata()));
        }

        public void metadataResolved(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Resolved metadata %s from %s", event.getMetadata(), event.getRepository()));
        }

        public void metadataResolving(
            RepositoryEvent event)
        {
            requireNonNull(event, "event cannot be null");
            logger.debug(String.format("Resolving metadata %s from %s", event.getMetadata(), event.getRepository()));
        }
    }
}
