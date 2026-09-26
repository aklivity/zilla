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
package io.aklivity.zilla.manager.internal.commands.install;

import static java.io.OutputStream.nullOutputStream;
import static java.lang.Integer.parseInt;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newInputStream;
import static java.util.Collections.emptyList;
import static java.util.Collections.list;
import static java.util.Comparator.reverseOrder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.apache.maven.settings.validation.DefaultSettingsValidator;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.manager.internal.ZpmCommand;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifact;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifactId;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmModule;

public abstract class ZpmDependencyCommand extends ZpmCommand
{
    protected static final String MODULE_INFO_JAVA_FILENAME = "module-info.java";
    protected static final String MODULE_INFO_CLASS_FILENAME = "module-info.class";

    private static final Pattern PATTERN_MAJOR_VERSION = Pattern.compile("(?<major>\\d+)\\.[^\\.]+\\.[^\\.]+");

    @Option(name = { "--verbose" },
        description = "Enable verbose logging")
    public Boolean verbose = false;

    @Option(name = {"--exclude-remote-repositories"},
        description = "Exclude remote Maven repositories")
    public boolean excludeRemoteRepos;

    protected ConsoleLogger newLogger(
        String name)
    {
        int level = silent ? ConsoleLogger.LEVEL_WARN : verbose ? ConsoleLogger.LEVEL_DEBUG : ConsoleLogger.LEVEL_INFO;
        return new ConsoleLogger(level, name);
    }

    protected List<RemoteRepository> remoteRepositories(
        ZpmConfiguration config,
        String localRepository) throws URISyntaxException, SettingsBuildingException
    {
        List<ZpmRepository> repositories = new ArrayList<>(config.repositories);

        if (localRepository != null)
        {
            repositories.add(0, new ZpmRepository(localRepository));
        }

        if (excludeRemoteRepos)
        {
            repositories.removeIf(r -> !r.location.startsWith("file:"));
        }

        File settingsFile = new File(String.format("/%s/.m2/settings.xml", System.getProperty("user.home")));

        DefaultSettingsBuilder settingsBuilder = new DefaultSettingsBuilder(
            new DefaultSettingsReader(), new DefaultSettingsWriter(), new DefaultSettingsValidator());
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setGlobalSettingsFile(settingsFile);
        request.setUserSettingsFile(settingsFile);

        Settings settings = settingsBuilder.build(request).getEffectiveSettings();

        return asRemoteRepositories(settings, repositories);
    }

    protected ZpmConfiguration readOrDefaultConfig(
        Path zpmFile) throws IOException
    {
        ZpmConfiguration config = new ZpmConfiguration();
        config.repositories = emptyList();
        config.imports = emptyList();
        config.dependencies = emptyList();

        Jsonb builder = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig().withFormatting(true))
            .build();

        if (Files.exists(zpmFile))
        {
            try (InputStream in = newInputStream(zpmFile))
            {
                config = builder.fromJson(in, ZpmConfiguration.class);
            }
        }

        return config;
    }

    protected List<RemoteRepository> asRemoteRepositories(
        Settings settings,
        List<ZpmRepository> repositories) throws URISyntaxException
    {
        final List<RemoteRepository> remoteRepositories = new ArrayList<>();

        for (ZpmRepository repository : repositories)
        {
            final String id = repository.id != null ? repository.id : new URI(repository.location).getHost();
            final RemoteRepository.Builder repoBuilder =
                new RemoteRepository.Builder(id, "default", repository.location)
                    .setRepositoryManager(true);

            final Server server = settings.getServer(id);
            if (server != null)
            {
                AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder()
                    .addUsername(server.getUsername())
                    .addPassword(server.getPassword());
                repoBuilder.setAuthentication(authenticationBuilder.build());
            }
            remoteRepositories.add(repoBuilder.build());
        }
        return remoteRepositories;
    }

    protected Collection<ZpmModule> discoverModules(
        Collection<ZpmArtifact> artifacts)
    {
        Path[] artifactPaths = artifacts.stream().map(a -> a.path).toArray(Path[]::new);
        Set<ModuleReference> references = new HashSet<>();

        for (Path path : artifactPaths)
        {
            ModuleFinder finder = ModuleFinder.of(path);
            references.addAll(finder.findAll());
        }

        Map<URI, ModuleDescriptor> descriptors = references
            .stream()
            .filter(r -> r.location().isPresent())
            .collect(Collectors.toMap(r -> r.location().get(), r -> r.descriptor()));

        Collection<ZpmModule> modules = new LinkedHashSet<>();
        for (ZpmArtifact artifact : artifacts)
        {
            URI artifactURI = artifact.path.toUri();
            ModuleDescriptor descriptor = descriptors.get(artifactURI);
            ZpmModule module = descriptor != null ? new ZpmModule(descriptor, artifact) : new ZpmModule(artifact);
            modules.add(module);
        }

        return modules;
    }

    protected void migrateUnnamed(
        Collection<ZpmModule> modules,
        ZpmModule delegate)
    {
        for (Iterator<ZpmModule> iterator = modules.iterator(); iterator.hasNext(); )
        {
            ZpmModule module = iterator.next();
            if (module.name == null)
            {
                delegate.paths.addAll(module.paths);
                delegate.depends.add(module.id);
                iterator.remove();
            }
        }

        assert !modules.stream().anyMatch(m -> m.name == null);
    }

    protected void delegateAutomatic(
        Collection<ZpmModule> modules,
        ZpmModule delegate)
    {
        Map<ZpmArtifactId, ZpmModule> modulesMap = new LinkedHashMap<>();
        modules.forEach(m -> modulesMap.put(m.id, m));

        for (ZpmModule module : modules)
        {
            if (module.automatic)
            {
                delegateModule(delegate, module, modulesMap::get);
            }
        }

        assert !modules.stream().anyMatch(m -> m.automatic && !m.delegating);
    }

    protected void delegateModule(
        ZpmModule delegate,
        ZpmModule module,
        Function<ZpmArtifactId, ZpmModule> lookup)
    {
        if (!module.delegating)
        {
            delegate.paths.addAll(module.paths);
            delegate.depends.add(module.id);
            module.paths.clear();
            module.delegating = true;

            for (ZpmArtifactId dependId : module.depends)
            {
                ZpmModule depend = lookup.apply(dependId);
                delegateModule(delegate, depend, lookup);
            }
        }
    }

    protected void generateSystemOnlyAutomatic(
        ConsoleLogger logger,
        Collection<ZpmModule> modules) throws IOException
    {
        Map<ZpmModule, Path> promotions = new IdentityHashMap<>();

        for (ZpmModule module : modules)
        {
            if (module.automatic && module.depends.isEmpty())
            {
                Path generatedModulesDir = generatedDir.resolve("modules");
                Path generatedModuleDir = generatedModulesDir.resolve(module.name);

                deleteDirectories(generatedModuleDir);

                Files.createDirectories(generatedModuleDir);

                assert module.paths.size() == 1;
                Path artifactPath = module.paths.iterator().next();

                ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();
                PrintStream nullOutput = new PrintStream(nullOutputStream());
                jdeps.run(
                    nullOutput,
                    nullOutput,
                    "--generate-open-module", generatedModulesDir.toString(),
                    artifactPath.toString());

                Path generatedModuleInfo = generatedModuleDir.resolve(MODULE_INFO_JAVA_FILENAME);
                if (Files.exists(generatedModuleInfo))
                {
                    logger.debug(String.format("Generating module info for system-only automatic module: %s", module.name));

                    long begin = System.nanoTime();

                    expandJar(generatedModuleDir, artifactPath);

                    ToolProvider javac = ToolProvider.findFirst("javac").get();

                    List<String> args = new ArrayList<>();
                    if (atLeastVersion(javac, 21))
                    {
                        args.add("-proc:none");
                    }

                    args.add("-d");
                    args.add(generatedModuleDir.toString());

                    args.add(generatedModuleInfo.toString());

                    javac.run(
                        nullOutput,
                        nullOutput,
                        args.toArray(String[]::new));

                    Path compiledModuleInfo = generatedModuleDir.resolve(MODULE_INFO_CLASS_FILENAME);
                    assert Files.exists(compiledModuleInfo);

                    Path generatedModulePath = generatedModulesDir.resolve(String.format("%s.jar", module.name));
                    JarEntry moduleInfoEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
                    moduleInfoEntry.setTime(318240000000L);
                    extendJar(artifactPath, generatedModulePath, moduleInfoEntry, compiledModuleInfo);

                    promotions.put(module, generatedModulePath);

                    logger.info(String.format("Generated module info for system-only automatic module: %s (%s)",
                        module.name, elapsed(begin)));
                }
            }
        }

        for (Map.Entry<ZpmModule, Path> entry : promotions.entrySet())
        {
            ZpmModule module = entry.getKey();
            Path newArtifactPath = entry.getValue();

            ModuleDescriptor descriptor = moduleDescriptor(newArtifactPath);
            assert descriptor != null;

            ZpmArtifact newArtifact = new ZpmArtifact(module.id, newArtifactPath, module.depends);
            ZpmModule promotion = new ZpmModule(descriptor, newArtifact);

            modules.remove(module);
            modules.add(promotion);
        }
    }

    protected ModuleDescriptor moduleDescriptor(
        Path archive)
    {
        ModuleDescriptor module = null;
        Set<ModuleReference> moduleRefs = ModuleFinder.of(archive).findAll();
        if (!moduleRefs.isEmpty())
        {
            module = moduleRefs.iterator().next().descriptor();
        }
        return module;
    }

    protected void expandJar(
        Path targetDir,
        Path sourcePath) throws IOException
    {
        try (JarFile sourceJar = new JarFile(sourcePath.toFile()))
        {
            for (JarEntry entry : list(sourceJar.entries()))
            {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir))
                {
                    throw new IOException("Bad zip entry");
                }
                else if (entry.isDirectory())
                {
                    createDirectories(entryPath);
                }
                else
                {
                    Path parentPath = entryPath.getParent();
                    if (!Files.exists(parentPath))
                    {
                        createDirectories(parentPath);
                    }

                    try (InputStream input = sourceJar.getInputStream(entry))
                    {
                        Files.write(entryPath, input.readAllBytes());
                    }
                }
            }
        }
    }

    protected void extendJar(
        Path sourcePath,
        Path targetPath,
        JarEntry newEntry,
        Path newEntryPath) throws IOException
    {
        try (JarFile sourceJar = new JarFile(sourcePath.toFile());
             JarOutputStream targetJar = new JarOutputStream(Files.newOutputStream(targetPath)))
        {
            for (JarEntry entry : list(sourceJar.entries()))
            {
                targetJar.putNextEntry(entry);
                if (!entry.isDirectory())
                {
                    try (InputStream input = sourceJar.getInputStream(entry))
                    {
                        targetJar.write(input.readAllBytes());
                    }
                }
                targetJar.closeEntry();
            }

            targetJar.putNextEntry(newEntry);
            targetJar.write(Files.readAllBytes(newEntryPath));
            targetJar.closeEntry();
        }
    }

    protected void deleteDirectories(
        Path dir) throws IOException
    {
        if (Files.exists(dir))
        {
            Files.walk(dir)
                .sorted(reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    protected static String elapsed(
        long begin)
    {
        return String.format("%.3fs", (System.nanoTime() - begin) * 1e-9);
    }

    protected static boolean atLeastVersion(
        ToolProvider tool,
        int major)
    {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        tool.run(
            new PrintWriter(out),
            new PrintWriter(err),
            "--version");

        Matcher matcher = PATTERN_MAJOR_VERSION.matcher(out.toString());
        return matcher.find() && parseInt(matcher.group("major")) >= major;
    }
}
