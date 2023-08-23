/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.manager.internal.settings.ZpmSecrets.decryptSecret;
import static java.io.OutputStream.nullOutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.util.Collections.emptyList;
import static java.util.Collections.list;
import static java.util.Collections.singletonMap;
import static java.util.Comparator.reverseOrder;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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
import java.util.stream.Stream;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.MessageLogger;
import org.apache.ivy.util.url.CredentialsStore;
import org.sonatype.plexus.components.cipher.PlexusCipherException;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.manager.internal.ZpmCommand;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifact;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifactId;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmCache;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmModule;
import io.aklivity.zilla.manager.internal.settings.ZpmCredentials;
import io.aklivity.zilla.manager.internal.settings.ZpmSecrets;
import io.aklivity.zilla.manager.internal.settings.ZpmSecurity;
import io.aklivity.zilla.manager.internal.settings.ZpmSettings;

@Command(
    name = "install",
    description = "Install dependencies")
public final class ZpmInstall extends ZpmCommand
{
    private static final String MODULE_INFO_JAVA_FILENAME = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILENAME = "module-info.class";

    private static final Map<String, String> DEFAULT_REALMS = initDefaultRealms();

    @Option(name = { "--debug" },
            description = "Link jdk.jdwp.agent module")
    public Boolean debug = false;

    @Option(name = { "--exclude-local-repository" },
            description = "Exclude the local Maven repository")
    public boolean excludeLocalRepo;

    @Option(name = { "--exclude-remote-repositories" },
            description = "Exclude remote Maven repositories")
    public boolean excludeRemoteRepos;

    @Option(name = { "--ignore-missing-dependencies" },
            hidden = true)
    public boolean ignoreMissingDependencies;

    @Override
    public void invoke()
    {
        int level = silent ? Message.MSG_WARN : Message.MSG_INFO;
        MessageLogger logger = new DefaultMessageLogger(level);
        Message.setDefaultLogger(logger);

        try
        {
            ZpmConfiguration config;

            Path zpmFile = configDir.resolve("zpm.json");

            logger.info(String.format("reading %s", zpmFile));
            config = readOrDefaultConfig(zpmFile);

            Path lockFile = lockDir.resolve("zpm-lock.json");
            logger.info(String.format("reading %s", lockFile));
            config = overrideConfigIfLocked(config, zpmFile, lockFile);

            logger.info("resolving dependencies");
            readSettings(settingsDir);
            createDirectories(cacheDir);
            List<ZpmRepository> repositories = new ArrayList<>(config.repositories);
            if (!excludeLocalRepo)
            {
                String localRepo = String.format("file://%s/.m2/repository", System.getProperty("user.home"));
                repositories.add(0, new ZpmRepository(localRepo));
            }

            if (excludeRemoteRepos)
            {
                repositories.removeIf(r -> !r.location.startsWith("file:"));
            }

            ZpmCache cache = new ZpmCache(repositories, cacheDir);
            Collection<ZpmArtifact> artifacts = cache.resolve(config.imports, config.dependencies);
            Map<ZpmDependency, ZpmDependency> resolvables = artifacts.stream()
                    .map(a -> a.id)
                    .collect(
                        toMap(
                            id -> ZpmDependency.of(id.group, id.artifact, null),
                            id -> ZpmDependency.of(id.group, id.artifact, id.version)));

            ZpmConfiguration resolved = new ZpmConfiguration();
            resolved.repositories = config.repositories;
            resolved.imports = null;
            resolved.dependencies = config.dependencies.stream()
                    .map(d -> ofNullable(resolvables.get(d)).orElse(d))
                    .collect(toList());

            if (!resolved.equals(config))
            {
                logger.info(String.format("writing %s", lockFile));
                writeLockFile(resolved, lockFile);
            }

            createDirectories(modulesDir);
            createDirectories(generatedDir);

            ZpmModule delegate = new ZpmModule();
            Collection<ZpmModule> modules = discoverModules(artifacts);
            migrateUnnamed(modules, delegate);
            generateSystemOnlyAutomatic(logger, modules);
            delegateAutomatic(modules, delegate);
            copyNonDelegating(modules);

            if (!delegate.paths.isEmpty())
            {
                generateDelegate(logger, delegate);
                generateDelegating(modules);
            }

            deleteDirectories(imageDir);
            linkModules(modules);
            logger.info("linked modules");

            generateLauncher();
            logger.info("generated launcher");
        }
        catch (Exception ex)
        {
            logger.error(String.format("Error: %s", ex.getMessage()));
            throw new RuntimeException(ex);
        }
        finally
        {
            if (!silent)
            {
                logger.sumupProblems();
            }
        }
    }

    private void readSettings(
        Path settingsDir) throws IOException, PlexusCipherException
    {
        Path settingsFile = settingsDir.resolve("settings.json");

        ZpmSettings settings = new ZpmSettings();
        settings.credentials = emptyList();

        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        if (Files.exists(settingsFile))
        {
            try (InputStream in = newInputStream(settingsFile))
            {
                settings = builder.fromJson(in, ZpmSettings.class);
            }
        }

        if (settings.credentials.size() > 0)
        {
            Path securityFile = settingsDir.resolve("security.json");

            ZpmSecurity security = new ZpmSecurity();

            if (Files.exists(securityFile))
            {
                try (InputStream in = newInputStream(securityFile))
                {
                    security = builder.fromJson(in, ZpmSecurity.class);
                }
            }

            security.secret = decryptSecret(security.secret, SYSTEM_PROPERTY_SEC_LOCATION);

            for (ZpmCredentials credentials : settings.credentials)
            {
                String realm = defaultRealmIfNecessary(credentials);
                String host = credentials.host;
                String username = credentials.username;
                String password = ZpmSecrets.decryptSecret(credentials.password, security.secret);

                CredentialsStore.INSTANCE.addCredentials(
                    realm,
                    host,
                    username,
                    password);
            }
        }
    }

    private ZpmConfiguration readOrDefaultConfig(
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

    private ZpmConfiguration overrideConfigIfLocked(
        ZpmConfiguration config,
        Path zpmFile,
        Path lockFile) throws IOException
    {
        if (Files.exists(lockFile) &&
            getLastModifiedTime(lockFile).compareTo(getLastModifiedTime(zpmFile)) >= 0)
        {
            Jsonb builder = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig().withFormatting(true))
                    .build();

            try (InputStream in = newInputStream(lockFile))
            {
                config = builder.fromJson(in, ZpmConfiguration.class);
            }
        }
        return config;
    }

    private void writeLockFile(
        ZpmConfiguration config,
        Path lockFile) throws IOException
    {
        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        createDirectories(lockDir);
        try (OutputStream out = newOutputStream(lockFile))
        {
            builder.toJson(config, out);
        }
    }

    private Collection<ZpmModule> discoverModules(
        Collection<ZpmArtifact> artifacts)
    {
        Path[] artifactPaths = artifacts.stream().map(a -> a.path).toArray(Path[]::new);
        Set<ModuleReference> references = ModuleFinder.of(artifactPaths).findAll();
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

    private void migrateUnnamed(
        Collection <ZpmModule> modules,
        ZpmModule delegate)
    {
        for (Iterator<ZpmModule> iterator = modules.iterator(); iterator.hasNext();)
        {
            ZpmModule module = iterator.next();
            if (module.name == null)
            {
                delegate.paths.addAll(module.paths);
                iterator.remove();
            }
        }

        assert !modules.stream().anyMatch(m -> m.name == null);
    }

    private void delegateAutomatic(
        Collection <ZpmModule> modules,
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

    private void delegateModule(
        ZpmModule delegate,
        ZpmModule module,
        Function<ZpmArtifactId, ZpmModule> lookup)
    {
        if (!module.delegating)
        {
            delegate.paths.addAll(module.paths);
            module.paths.clear();
            module.delegating = true;

            for (ZpmArtifactId dependId : module.depends)
            {
                ZpmModule depend = lookup.apply(dependId);
                delegateModule(delegate, depend, lookup);
            }
        }
    }

    private void generateSystemOnlyAutomatic(
        MessageLogger logger,
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
                    logger.info(String.format("Generated module info for system-only automatic module: %s", module.name));

                    expandJar(generatedModuleDir, artifactPath);

                    ToolProvider javac = ToolProvider.findFirst("javac").get();
                    javac.run(
                            nullOutput,
                            nullOutput,
                            "-d", generatedModuleDir.toString(),
                            generatedModuleInfo.toString());

                    Path compiledModuleInfo = generatedModuleDir.resolve(MODULE_INFO_CLASS_FILENAME);
                    assert Files.exists(compiledModuleInfo);

                    Path generatedModulePath = generatedModulesDir.resolve(String.format("%s.jar", module.name));
                    JarEntry moduleInfoEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
                    moduleInfoEntry.setTime(318240000000L);
                    extendJar(artifactPath, generatedModulePath, moduleInfoEntry, compiledModuleInfo);

                    promotions.put(module, generatedModulePath);
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

    private void copyNonDelegating(
        Collection<ZpmModule> modules) throws IOException
    {
        for (ZpmModule module : modules)
        {
            if (!module.delegating)
            {
                assert module.paths.size() == 1;
                Path artifactPath = module.paths.iterator().next();
                Path modulePath = modulePath(module);
                Files.copy(artifactPath, modulePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void generateDelegate(
        MessageLogger logger,
        ZpmModule delegate) throws IOException
    {
        Path generatedModulesDir = generatedDir.resolve("modules");
        Path generatedDelegateDir = generatedModulesDir.resolve(delegate.name);
        Files.createDirectories(generatedModulesDir);

        Path generatedDelegatePath = generatedModulesDir.resolve(String.format("%s.jar", delegate.name));
        try (JarOutputStream moduleJar = new JarOutputStream(Files.newOutputStream(generatedDelegatePath)))
        {
            Path moduleInfoPath = Paths.get(MODULE_INFO_CLASS_FILENAME);
            Path manifestPath = Paths.get("META-INF", "MANIFEST.MF");
            Path servicesPath = Paths.get("META-INF", "services");
            Path excludedPackage = Paths.get("org", "eclipse", "yasson", "internal", "components");
            String excludedClass = "BeanManagerInstanceCreator";
            Set<String> entryNames = new HashSet<>();
            Map<String, String> services = new HashMap<>();
            for (Path path : delegate.paths)
            {
                try (JarFile artifactJar = new JarFile(path.toFile()))
                {
                    for (JarEntry entry : Collections.list(artifactJar.entries()))
                    {
                        String entryName = entry.getName();
                        Path entryPath = Paths.get(entryName);
                        if (entryPath.equals(moduleInfoPath) ||
                            entryPath.equals(manifestPath) ||
                            (entryPath.startsWith(excludedPackage)) &&
                             entryPath.getFileName().toString().startsWith(excludedClass))
                        {
                            continue;
                        }

                        try (InputStream input = artifactJar.getInputStream(entry))
                        {
                            if (entryPath.startsWith(servicesPath) &&
                                entryPath.getNameCount() - servicesPath.getNameCount() == 1)
                            {
                                Path servicePath = servicesPath.relativize(entryPath);
                                assert servicePath.getNameCount() == 1;
                                String serviceName = servicePath.toString();
                                String serviceImpl = new String(input.readAllBytes(), UTF_8);
                                String existing = services.getOrDefault(serviceName, "");
                                services.put(serviceName, existing.concat(serviceImpl));
                            }
                            else if (entryNames.add(entryName))
                            {
                                moduleJar.putNextEntry(entry);
                                moduleJar.write(input.readAllBytes());
                                moduleJar.closeEntry();
                            }
                        }
                    }
                }
            }

            for (Map.Entry<String, String> service : services.entrySet())
            {
                String serviceName = service.getKey();
                Path servicePath = servicesPath.resolve(serviceName);
                String serviceImpl = service.getValue();

                JarEntry newEntry = new JarEntry(servicePath.toString());
                newEntry.setTime(318240000000L);
                moduleJar.putNextEntry(newEntry);
                moduleJar.write(serviceImpl.getBytes(UTF_8));
                moduleJar.closeEntry();
            }
        }

        List<String> jdepsArgs = Arrays.asList(
            "--generate-module-info", generatedModulesDir.toString(),
            generatedDelegatePath.toString());
        if (ignoreMissingDependencies)
        {
            jdepsArgs = new LinkedList<>(jdepsArgs);
            jdepsArgs.add(0, "--ignore-missing-deps");
        }
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();
        jdeps.run(
            System.out,
            System.err,
            jdepsArgs.toArray(String[]::new));

        Path generatedModuleInfo = generatedDelegateDir.resolve(MODULE_INFO_JAVA_FILENAME);

        if (!Files.exists(generatedModuleInfo))
        {
            throw new IOException("Failed to generate module info for delegate module");
        }

        logger.info(String.format("Generated module info for delegate module\n"));

        String moduleInfoContents = Files.readString(generatedModuleInfo);
        Pattern pattern = Pattern.compile("(?:provides\\s+)([^\\s]+)(?:\\s+with)");
        Matcher matcher = pattern.matcher(moduleInfoContents);
        List<String> uses = new ArrayList<>();
        while (matcher.find())
        {
            String service = matcher.group(1);
            uses.add(String.format("uses %s;", service));
        }

        if (!uses.isEmpty())
        {
            Files.writeString(generatedModuleInfo,
                    moduleInfoContents.replace(
                            "}",
                            String.join("\n", uses) + "\n}"));
        }

        expandJar(generatedDelegateDir, generatedDelegatePath);

        ToolProvider javac = ToolProvider.findFirst("javac").get();
        javac.run(
                System.out,
                System.err,
                "-d", generatedDelegateDir.toString(),
                generatedModuleInfo.toString());

        Path compiledModuleInfo = generatedDelegateDir.resolve(MODULE_INFO_CLASS_FILENAME);
        assert Files.exists(compiledModuleInfo);

        Path delegatePath = modulePath(delegate);
        JarEntry moduleInfoEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
        moduleInfoEntry.setTime(318240000000L);
        extendJar(generatedDelegatePath, delegatePath, moduleInfoEntry, compiledModuleInfo);
    }

    private void generateDelegating(
        Collection<ZpmModule> modules) throws IOException
    {
        for (ZpmModule module : modules)
        {
            if (module.delegating)
            {
                Path generatedModulesDir = generatedDir.resolve("modules");
                Path generatedModuleDir = generatedModulesDir.resolve(module.name);
                Files.createDirectories(generatedModuleDir);

                Path generatedModuleInfo = generatedModuleDir.resolve(MODULE_INFO_JAVA_FILENAME);
                Files.write(generatedModuleInfo, Arrays.asList(
                        String.format("open module %s {", module.name),
                        String.format("    requires transitive %s;", ZpmModule.DELEGATE_NAME),
                        "}"));

                ToolProvider javac = ToolProvider.findFirst("javac").get();
                javac.run(
                        System.out,
                        System.err,
                        "-d", generatedModuleDir.toString(),
                        "--module-path", modulesDir.toString(),
                        generatedModuleInfo.toString());

                Path modulePath = modulePath(module);
                try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(modulePath)))
                {
                    JarEntry newEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
                    newEntry.setTime(318240000000L);
                    jar.putNextEntry(newEntry);
                    jar.write(Files.readAllBytes(generatedModuleDir.resolve(MODULE_INFO_CLASS_FILENAME)));
                    jar.closeEntry();
                }
            }
        }
    }

    private void linkModules(
        Collection<ZpmModule> modules) throws IOException
    {
        ToolProvider jlink = ToolProvider.findFirst("jlink").get();

        List<String> extraModuleNames = new ArrayList<>();
        if (debug)
        {
            extraModuleNames.add("jdk.jdwp.agent");
        }

        Stream<String> moduleNames = Stream.concat(modules.stream().map(m -> m.name), extraModuleNames.stream());

        List<String> args = new ArrayList<>(Arrays.asList(
            "--module-path", modulesDir.toString(),
            "--output", imageDir.toString(),
            "--no-header-files",
            "--no-man-pages",
            "--compress", "2",
            "--add-modules", moduleNames.collect(Collectors.joining(","))));

        args.add("--ignore-signing-information");

        if (!debug)
        {
            args.add("--strip-debug");
        }

        if (!silent)
        {
            args.add("--verbose");
        }

        jlink.run(
            System.out,
            System.err,
            args.toArray(String[]::new));
    }

    private void generateLauncher() throws IOException
    {
        Path zillaPath = launcherDir.resolve("zilla");
        Files.write(zillaPath, Arrays.asList(
                "#!/bin/sh",
                "cd \"${0%/*}\"",
                String.format(String.join(" ", Arrays.asList(
                    "exec %s/bin/java",
                    "--add-opens java.base/sun.nio.ch=org.agrona.core",
                    "$JAVA_OPTIONS",
                    "-m io.aklivity.zilla.runtime.command/io.aklivity.zilla.runtime.command.internal.ZillaMain \"$@\"")),
                    imageDir)));
        zillaPath.toFile().setExecutable(true);
    }

    private ModuleDescriptor moduleDescriptor(
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

    private Path modulePath(
        ZpmModule module)
    {
        return modulesDir.resolve(String.format("%s.jar", module.name));
    }

    private void expandJar(
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

    private void extendJar(
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

    private void deleteDirectories(
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

    private String defaultRealmIfNecessary(
        ZpmCredentials credentials)
    {
        return ofNullable(credentials.realm)
            .orElse(DEFAULT_REALMS.get(credentials.host));
    }

    private static Map<String, String> initDefaultRealms()
    {
        return singletonMap("maven.pkg.github.com", "GitHub Package Registry");
    }
}
