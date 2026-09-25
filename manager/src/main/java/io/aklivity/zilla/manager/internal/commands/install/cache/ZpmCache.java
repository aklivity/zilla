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
package io.aklivity.zilla.manager.internal.commands.install.cache;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.eclipse.aether.ConfigurationProperties.CONNECT_TIMEOUT;
import static org.eclipse.aether.ConfigurationProperties.REQUEST_TIMEOUT;
import static org.eclipse.aether.util.graph.transformer.ConflictResolver.CONFIG_PROP_VERBOSE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.impl.scope.OptionalDependencySelector;
import org.eclipse.aether.internal.impl.scope.ScopeDependencySelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.transport.apache.ApacheTransporterFactory;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.ConfigurableVersionSelector;
import org.eclipse.aether.util.graph.transformer.ConflictIdSorter;
import org.eclipse.aether.util.graph.transformer.ConflictMarker;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.visitor.NodeListGenerator;
import org.eclipse.aether.util.graph.visitor.PreorderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;

public final class ZpmCache
{
    // ZPM resolves dependencies in a single, one-shot process that needs no cross-process
    // or cross-thread locking. maven-resolver's sync-context lock factories deadlock here
    // while resolving SNAPSHOT metadata: a shared lock on the runtime metadata cannot be
    // acquired within the 30s timeout (observed with both the default "file-lock" factory
    // and the in-JVM "rwlock-local" factory). Disable locking with the "noop" factory.
    private static final String CONFIG_PROP_NAMED_LOCK_FACTORY = "aether.syncContext.named.factory";

    // maven-resolver defaults REQUEST_TIMEOUT to 1800000ms, so a transfer that connects and
    // then stalls waits half an hour before failing. Resolution is otherwise seconds long,
    // so that reads as a deadlock and outlives most CI step budgets: one observed install
    // sat in resolveOptional for 19 minutes with no output before the job was killed. Bound
    // both so a stuck transfer fails while there is still budget left to report it.
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int REQUEST_TIMEOUT_MS = 60000;

    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession session;
    private final RepositorySystemSession optionalSession;

    private final List<RemoteRepository> repositories;
    private final boolean excludeRemote;
    private final ConsoleLogger logger;
    private final Path directory;
    private final Set<Path> resolvedPaths;

    public ZpmCache(
        List<RemoteRepository> repositories,
        boolean excludeRemote,
        Path directory,
        ConsoleLogger logger)
    {
        this.logger = logger;
        this.directory = directory.toAbsolutePath().normalize();
        this.resolvedPaths = new LinkedHashSet<>();
        this.repositorySystem = ZpmSupplierRepositorySystemFactory.newRepositorySystem();
        this.session = newRepositorySystemSession(repositorySystem, directory, excludeRemote, false);
        this.optionalSession = newRepositorySystemSession(repositorySystem, directory, excludeRemote, true);

        this.repositories = repositories;
        this.excludeRemote = excludeRemote;
    }

    public List<ZpmArtifact> resolve(
        List<ZpmDependency> imports,
        List<ZpmDependency> dependencies)
    {
        Map<ZpmDependency, String> imported = new HashMap<>();
        List<Dependency> managedDependencies = new ArrayList<>();
        List<RemoteRepository> aggregatedRepositories = new ArrayList<>(repositories);
        readImports(imports, session, imported, managedDependencies, aggregatedRepositories);

        List<Artifact> roots = new ArrayList<>();
        for (ZpmDependency dep : dependencies)
        {
            String version = ofNullable(dep.version).orElse(imported.get(dep));
            roots.add(new DefaultArtifact(dep.groupId, dep.artifactId, "jar", version));
        }

        return resolve(roots, managedDependencies, aggregatedRepositories, session, false);
    }

    public List<ZpmArtifact> resolveOptional(
        List<ZpmDependency> imports,
        Collection<ZpmArtifactId> delegated,
        Collection<ZpmArtifact> resolved)
    {
        Map<ZpmDependency, String> imported = new HashMap<>();
        List<Dependency> importedDependencies = new ArrayList<>();
        List<RemoteRepository> aggregatedRepositories = new ArrayList<>(repositories);
        readImports(imports, optionalSession, imported, importedDependencies, aggregatedRepositories);

        Map<String, Dependency> managed = new LinkedHashMap<>();
        importedDependencies.forEach(d -> managed.put(managementKey(d.getArtifact()), d));
        resolved.forEach(a ->
        {
            Artifact artifact = new DefaultArtifact(a.id.group, a.id.artifact, "jar", a.id.version);
            managed.put(managementKey(artifact), new Dependency(artifact, ""));
        });

        List<Artifact> roots = new ArrayList<>();
        delegated.forEach(id -> roots.add(new DefaultArtifact(id.group, id.artifact, "jar", id.version)));

        return resolve(roots, new ArrayList<>(managed.values()), aggregatedRepositories, optionalSession, true);
    }

    public void export(
        Path target) throws IOException
    {
        for (Path path : resolvedPaths)
        {
            Path exported = target.resolve(directory.relativize(path).toString());
            Files.createDirectories(exported.getParent());
            Files.copy(path, exported, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void readImports(
        List<ZpmDependency> imports,
        RepositorySystemSession session,
        Map<ZpmDependency, String> imported,
        List<Dependency> managedDependencies,
        List<RemoteRepository> aggregatedRepositories)
    {
        if (imports != null)
        {
            for (ZpmDependency imp : imports)
            {
                Artifact artifact = new DefaultArtifact(imp.groupId, imp.artifactId, "pom", imp.version);
                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                repositories.forEach(descriptorRequest::addRepository);
                descriptorRequest.setArtifact(artifact);
                try
                {
                    ArtifactDescriptorResult descriptorResult =
                        repositorySystem.readArtifactDescriptor(session, descriptorRequest);
                    if (!excludeRemote)
                    {
                        aggregatedRepositories.addAll(descriptorResult.getRepositories());
                    }
                    List<Dependency> bomManaged = descriptorResult.getManagedDependencies();
                    bomManaged.forEach(dep ->
                    {
                        final Artifact managedArtifact = dep.getArtifact();
                        imported.put(ZpmDependency.of(managedArtifact.getGroupId(), managedArtifact.getArtifactId(), null),
                            managedArtifact.getVersion());

                    });
                    managedDependencies.addAll(bomManaged);
                }
                catch (ArtifactDescriptorException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private List<ZpmArtifact> resolve(
        List<Artifact> roots,
        List<Dependency> managedDependencies,
        List<RemoteRepository> aggregatedRepositories,
        RepositorySystemSession session,
        boolean lenient)
    {
        final List<ZpmArtifact> artifacts = new ArrayList<>();
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setManagedDependencies(managedDependencies);
        roots.forEach(artifact -> collectRequest.addDependency(new Dependency(artifact, "compile")));
        aggregatedRepositories.forEach(collectRequest::addRepository);

        DependencyResult result;
        try
        {
            CollectResult collectResult = repositorySystem.collectDependencies(session, collectRequest);
            DependencyRequest dependencyRequest = new DependencyRequest(collectResult.getRoot(), null);
            result = repositorySystem.resolveDependencies(session, dependencyRequest);
        }
        catch (DependencyResolutionException e)
        {
            // when resolving the optional validation-only tree, tolerate artifacts that cannot be
            // resolved (for example when remote repositories are excluded) and continue with whatever
            // was resolved successfully
            if (!lenient)
            {
                throw new RuntimeException("Failed to resolve dependencies", e);
            }
            logger.warn(String.format("Partially resolved optional dependencies: %s", e.getMessage()));
            result = e.getResult();
        }
        catch (Exception e)
        {
            // the optional validation-only tree is best-effort; if it cannot be collected at all
            // (for example offline with missing descriptors) skip validation rather than fail install
            if (!lenient)
            {
                throw new RuntimeException("Failed to resolve dependencies", e);
            }
            logger.warn(String.format("Skipped optional dependencies: %s", e.getMessage()));
            result = null;
        }

        if (result == null)
        {
            return artifacts;
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
                if (artifact.getPath() != null)
                {
                    artifacts.add(new ZpmArtifact(id, artifact.getPath(), depends));
                }
            }
        });

        return artifacts;
    }

    private RepositorySystemSession newRepositorySystemSession(
        RepositorySystem system,
        Path dir,
        boolean excludeRemote,
        boolean includeOptional)
    {
        ConflictResolver conflictResolver = new ConflictResolver(
            new ConfigurableVersionSelector(new ConfigurableVersionSelector.Nearest()),
            new ConflictResolver.ScopeSelector()
            {
                @Override
                public void selectScope(
                    ConflictResolver.ConflictContext context)
                {
                    if (context.getWinner() != null && context.getWinner().getDependency() != null)
                    {
                        context.setScope(context.getWinner().getDependency().getScope());
                    }
                }
            },
            new SimpleOptionalitySelector(),
            new ConflictResolver.ScopeDeriver()
            {
                @Override
                public void deriveScope(
                    ConflictResolver.ScopeContext context)
                {
                    // Pass the parent's target scope down to the child vertex
                    context.setDerivedScope(context.getChildScope());
                }
            }
        );

        RepositorySystemSession.SessionBuilder builder = new SessionBuilderSupplier(system)
            .get()
                // the simple layout reuses artifacts regardless of which repository originally provided them,
                // so an existing Maven local repository serves as the cache without re-downloading
                .withLocalRepositories(new LocalRepository(dir, "simple"))
                .setDependencyGraphTransformer(
                    new ChainedDependencyGraphTransformer(
                        new ConflictMarker(),
                        new ConflictIdSorter(),
                        conflictResolver))
                .setDependencySelector(includeOptional
                    // the validation-only tree also pulls in provided-scope dependencies (for example
                    // Netty's provided org.jetbrains:annotations-java5) so jdeps can resolve their
                    // static bytecode references; these artifacts are never linked into the runtime image
                    ? new AndDependencySelector(
                        new ExclusionDependencySelector(),
                        ScopeDependencySelector.fromRoot(null, List.of("test")))
                    : new AndDependencySelector(
                        OptionalDependencySelector.fromRoot(),
                        new ExclusionDependencySelector(),
                        ScopeDependencySelector.fromRoot(null, List.of("test", "provided"))))
                .setRepositoryListener(new ZpmConsoleRepositoryListener())
                .setTransferListener(new ZpmConsoleTransferListener())
                .setConfigProperty(CONFIG_PROP_VERBOSE, "true")
                .setConfigProperty(CONFIG_PROP_NAMED_LOCK_FACTORY, "noop")
                .setConfigProperty(CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS)
                .setConfigProperty(REQUEST_TIMEOUT, REQUEST_TIMEOUT_MS)
                .setIgnoreArtifactDescriptorRepositories(excludeRemote);

        if (includeOptional)
        {
            // the validation-only tree reaches artifacts whose descriptors may be missing or unparseable
            // (for example an unresolved classifier property); skip those rather than drop the whole tree
            builder.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));
        }

        return builder.build();
    }

    private static String managementKey(
        Artifact artifact)
    {
        return String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
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
                protected Map<String, TransporterFactory> createTransporterFactories()
                {
                    Map<String, TransporterFactory> result = super.createTransporterFactories();
                    result.put(
                        ApacheTransporterFactory.NAME,
                        new ApacheTransporterFactory(getChecksumExtractor(), getPathProcessor()));
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
            Path path = ofNullable(event.getPath()).orElse(event.getArtifact().getPath());
            if (path != null && event.getException() == null)
            {
                Path normalized = path.toAbsolutePath().normalize();
                if (normalized.startsWith(directory))
                {
                    resolvedPaths.add(normalized);
                }
            }
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
