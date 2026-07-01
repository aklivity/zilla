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
package io.aklivity.zilla.manager.internal.commands.install;

import static java.io.OutputStream.nullOutputStream;
import static java.lang.Integer.parseInt;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.apache.maven.settings.io.SettingsReader;
import org.apache.maven.settings.io.SettingsWriter;
import org.apache.maven.settings.validation.DefaultSettingsValidator;
import org.apache.maven.settings.validation.SettingsValidator;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.manager.internal.ZpmCommand;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifact;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifactId;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmCache;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmModule;

@Command(
    name = "install",
    description = "Install dependencies")
public final class ZpmInstall extends ZpmCommand
{
    private static final String MODULE_INFO_JAVA_FILENAME = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILENAME = "module-info.class";

    private static final Pattern PATTERN_MAJOR_VERSION = Pattern.compile("(?<major>\\d+)\\.[^\\.]+\\.[^\\.]+");

    private static final Map<String, String> DEFAULT_REALMS = initDefaultRealms();

    @Option(name = { "--verbose" },
        description = "Enable verbose logging")
    public Boolean verbose = false;

    @Option(name = {"--debug"},
        description = "Link jdk.jdwp.agent module")
    public Boolean debug = false;

    @Option(name = {"--instrument"},
        description = "Link java.instrument module",
        hidden = true)
    public Boolean instrument = false;

    @Option(name = {"--exclude-local-repository"},
        description = "Exclude the local Maven repository")
    public boolean excludeLocalRepo;

    @Option(name = {"--exclude-remote-repositories"},
        description = "Exclude remote Maven repositories")
    public boolean excludeRemoteRepos;

    @Option(name = {"--ignore-missing-dependencies"},
        hidden = true)
    public boolean ignoreMissingDependencies;

    @Option(name = {"--unsafe-memory-access"},
        hidden = true)
    public String unsafeMemoryAccess = "deny";

    @Override
    public void invoke()
    {
        int level = silent ? ConsoleLogger.LEVEL_WARN : verbose ? ConsoleLogger.LEVEL_DEBUG : ConsoleLogger.LEVEL_INFO;
        ConsoleLogger logger = new ConsoleLogger(level, "ZpmInstall");

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
            createDirectories(cacheDir);
            List<ZpmRepository> repositories = new ArrayList<>(config.repositories);

            final String home = System.getProperty("user.home");
            if (!excludeLocalRepo)
            {
                String localRepo = String.format("file://%s/.m2/repository", home);
                repositories.add(0, new ZpmRepository(localRepo));
            }

            if (excludeRemoteRepos)
            {
                repositories.removeIf(r -> !r.location.startsWith("file:"));
            }

            File settingsFile = new File(String.format("/%s/.m2/settings.xml", home));

            SettingsReader settingsReader = new DefaultSettingsReader();
            SettingsWriter settingsWriter = new DefaultSettingsWriter();
            SettingsValidator settingsValidator = new DefaultSettingsValidator();

            DefaultSettingsBuilder settingsBuilder = new DefaultSettingsBuilder(
                settingsReader, settingsWriter, settingsValidator);
            DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
            request.setGlobalSettingsFile(settingsFile);
            request.setUserSettingsFile(settingsFile);

            SettingsBuildingResult result = settingsBuilder.build(request);
            Settings settings = result.getEffectiveSettings();

            List<RemoteRepository> remoteRepositories = asRemoteRepositories(settings, repositories);

            ZpmCache cache = new ZpmCache(remoteRepositories, excludeRemoteRepos, cacheDir, logger);
            Collection<ZpmArtifact> artifacts = cache.resolve(config.imports, config.dependencies);

            Map<ZpmDependency, ZpmDependency> resolvables = artifacts.stream()
                .map(a -> a.id)
                .collect(toMap(
                    id -> ZpmDependency.of(id.group, id.artifact, null),
                    id -> ZpmDependency.of(id.group, id.artifact, id.version),
                    (first, second) -> second));

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
                Collection<ZpmArtifact> optional = cache.resolveOptional(config.imports, config.dependencies);
                generateDelegate(logger, delegate, optional);
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
    }

    private List<RemoteRepository> asRemoteRepositories(
        Settings settings,
        List<ZpmRepository> repositories) throws URISyntaxException
    {
        final List<RemoteRepository> remoteRepositories = new ArrayList<>();

        for (ZpmRepository repository : repositories)
        {
            final String host = new URI(repository.location).getHost();
            final RemoteRepository.Builder repoBuilder =
                new RemoteRepository.Builder(host, "default", repository.location)
                    .setRepositoryManager(true)
                    .setId(host);

            final Server server = settings.getServer(host);
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

    private void migrateUnnamed(
        Collection<ZpmModule> modules,
        ZpmModule delegate)
    {
        for (Iterator<ZpmModule> iterator = modules.iterator(); iterator.hasNext(); )
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
                    logger.info(String.format("Generated module info for system-only automatic module: %s", module.name));

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
        ConsoleLogger logger,
        ZpmModule delegate,
        Collection<ZpmArtifact> optional) throws IOException
    {
        Path generatedModulesDir = generatedDir.resolve("modules");
        Path generatedDelegateDir = generatedModulesDir.resolve(delegate.name);
        Files.createDirectories(generatedModulesDir);

        Path generatedDelegatePath = generatedModulesDir.resolve(String.format("%s.jar", delegate.name));
        generateDelegateJar(delegate, generatedDelegatePath);

        // Merge the optional dependency tree resolved via Aether into a single validation-only module so
        // jdeps can resolve the delegate's static references to optional third-party libraries (for
        // example Netty's Brotli/LZ4/Zstd/Protobuf codecs). As one flat automatic module it avoids the
        // split-package and inter-module resolution failures of supplying the optional jars separately,
        // and lets jdeps run strictly: any reference not satisfied by the delegate or this module is a
        // genuinely missing dependency rather than one silently ignored.
        Path optionalModulesDir = generatedDir.resolve("optional-modules");
        deleteDirectories(optionalModulesDir);
        Files.createDirectories(optionalModulesDir);
        String optionalName = String.format("%s.optional", delegate.name);
        Path optionalPath = optionalModulesDir.resolve(String.format("%s.jar", optionalName));
        generateDelegateOptional(optionalPath, optional, jarPackages(generatedDelegatePath));

        deleteDirectories(generatedDelegateDir);

        Path generatedModuleInfo = generatedDelegateDir.resolve(MODULE_INFO_JAVA_FILENAME);
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();

        boolean strict = !ignoreMissingDependencies;
        if (strict)
        {
            int result = jdeps.run(
                System.out,
                System.err,
                "--generate-module-info", generatedModulesDir.toString(),
                "--module-path", optionalModulesDir.toString(),
                generatedDelegatePath.toString());
            strict = result == 0 && Files.exists(generatedModuleInfo);
        }

        if (!strict)
        {
            // jdeps could not resolve every reference against the optional dependency tree (for example
            // offline with an incomplete tree); fall back to generating while ignoring missing
            // dependencies and report what forced the fallback so accidental prunes remain visible
            Set<String> missing = missingDependencies(generatedDelegatePath, optionalModulesDir);
            if (!missing.isEmpty())
            {
                String details = missing.stream()
                    .sorted()
                    .map(c -> String.format("    %s", c))
                    .collect(Collectors.joining("\n"));
                logger.warn(String.format(
                    "delegate module references %d dependencies not resolved by the optional dependency tree;" +
                    " generating module info while ignoring them:%n%s", missing.size(), details));
            }
            deleteDirectories(generatedDelegateDir);
            jdeps.run(
                System.out,
                System.err,
                "--generate-module-info", generatedModulesDir.toString(),
                "--ignore-missing-deps",
                generatedDelegatePath.toString());
        }

        if (!Files.exists(generatedModuleInfo))
        {
            throw new IOException("Failed to generate module info for delegate module");
        }

        logger.info(String.format("Generated module info for delegate module\n"));

        String moduleInfoContents = Files.readString(generatedModuleInfo);

        // drop the requires on the validation-only optional module; it is never linked into the image
        moduleInfoContents = moduleInfoContents.replaceAll(
            String.format("(?m)^\\s*requires\\s+(transitive\\s+)?%s\\s*;\\R?", Pattern.quote(optionalName)), "");

        // keep only service providers whose service interface is present in the delegate module, pruning
        // phantom provides declarations whose interface lives solely in the optional dependency tree
        Set<String> delegateClasses = jarClasses(generatedDelegatePath);
        Pattern providesPattern = Pattern.compile("(?m)^\\s*provides\\s+([^\\s;]+)\\s+with\\s+[^;]+;\\R?");
        Matcher providesMatcher = providesPattern.matcher(moduleInfoContents);
        StringBuilder cleanedModuleInfo = new StringBuilder();

        List<String> uses = new ArrayList<>();

        while (providesMatcher.find())
        {
            String serviceInterface = providesMatcher.group(1);
            if (delegateClasses.contains(serviceInterface))
            {
                providesMatcher.appendReplacement(cleanedModuleInfo, Matcher.quoteReplacement(providesMatcher.group(0)));
                uses.add(String.format("    uses %s;", serviceInterface));
            }
            else
            {
                providesMatcher.appendReplacement(cleanedModuleInfo, "");
            }
        }
        providesMatcher.appendTail(cleanedModuleInfo);
        moduleInfoContents = cleanedModuleInfo.toString();

        if (!uses.isEmpty())
        {
            int lastBraceIndex = moduleInfoContents.lastIndexOf('}');
            if (lastBraceIndex != -1)
            {
                moduleInfoContents = moduleInfoContents.substring(0, lastBraceIndex) +
                    String.join("\n", uses) +
                    "\n}";
            }
        }

        Files.writeString(generatedModuleInfo, moduleInfoContents);

        expandJar(generatedDelegateDir, generatedDelegatePath);

        ToolProvider javac = ToolProvider.findFirst("javac").get();

        List<String> args = new ArrayList<>();
        if (atLeastVersion(javac, 21))
        {
            args.add("-proc:none");
        }

        args.add("-d");
        args.add(generatedDelegateDir.toString());

        args.add(generatedModuleInfo.toString());

        javac.run(
            System.out,
            System.err,
            args.toArray(String[]::new));

        Path compiledModuleInfo = generatedDelegateDir.resolve(MODULE_INFO_CLASS_FILENAME);
        assert Files.exists(compiledModuleInfo);

        Path delegatePath = modulePath(delegate);
        JarEntry moduleInfoEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
        moduleInfoEntry.setTime(318240000000L);
        extendJar(generatedDelegatePath, delegatePath, moduleInfoEntry, compiledModuleInfo);
    }

    private void generateDelegateJar(
        ZpmModule delegate,
        Path generatedDelegatePath) throws IOException
    {
        try (JarOutputStream moduleJar = new JarOutputStream(Files.newOutputStream(generatedDelegatePath)))
        {
            Path moduleInfoPath = Paths.get(MODULE_INFO_CLASS_FILENAME);
            Path manifestPath = Paths.get("META-INF", "MANIFEST.MF");
            Path servicesPath = Paths.get("META-INF", "services");
            String packageInfoName = "package-info.class";
            Path excludedPackage = Paths.get("org", "eclipse", "yasson", "internal", "components");
            String excludedClass = "BeanManagerInstanceCreator";
            Set<String> entryNames = new HashSet<>();
            Map<String, List<String>> services = new HashMap<>();
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
                            entryPath.endsWith(packageInfoName) ||
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
                                services.computeIfAbsent(serviceName, s -> new ArrayList<>())
                                    .addAll(Arrays.asList(serviceImpl.split("\\R")));
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

            for (Map.Entry<String, List<String>> service : services.entrySet())
            {
                String serviceName = service.getKey();
                Path servicePath = servicesPath.resolve(serviceName);
                String serviceImpl = String.join("\n", service.getValue());

                JarEntry newEntry = new JarEntry(servicePath.toString());
                newEntry.setTime(318240000000L);
                moduleJar.putNextEntry(newEntry);
                moduleJar.write(serviceImpl.getBytes(UTF_8));
                moduleJar.closeEntry();
            }
        }
    }

    private void generateDelegateOptional(
        Path optionalPath,
        Collection<ZpmArtifact> optional,
        Set<String> delegatePackages) throws IOException
    {
        // packages owned by JDK system modules must be excluded, otherwise the merged automatic module
        // would split a package the boot layer already exports (for example javax.xml)
        Set<String> systemPackages = ModuleFinder.ofSystem().findAll().stream()
            .flatMap(reference -> reference.descriptor().packages().stream())
            .collect(Collectors.toSet());

        Set<String> entryNames = new HashSet<>();
        try (JarOutputStream optionalJar = new JarOutputStream(Files.newOutputStream(optionalPath)))
        {
            for (ZpmArtifact artifact : optional)
            {
                Path path = artifact.path;
                if (path == null || !Files.exists(path))
                {
                    continue;
                }

                try (JarFile artifactJar = new JarFile(path.toFile()))
                {
                    for (JarEntry entry : list(artifactJar.entries()))
                    {
                        String entryName = entry.getName();
                        if (entry.isDirectory() ||
                            !entryName.endsWith(".class") ||
                            entryName.endsWith(MODULE_INFO_CLASS_FILENAME) ||
                            entryName.startsWith("META-INF/"))
                        {
                            continue;
                        }

                        int lastSlash = entryName.lastIndexOf('/');
                        String packageName = lastSlash < 0 ? "" : entryName.substring(0, lastSlash).replace('/', '.');
                        if (delegatePackages.contains(packageName) ||
                            systemPackages.contains(packageName) ||
                            !entryNames.add(entryName))
                        {
                            continue;
                        }

                        try (InputStream input = artifactJar.getInputStream(entry))
                        {
                            JarEntry newEntry = new JarEntry(entryName);
                            newEntry.setTime(318240000000L);
                            optionalJar.putNextEntry(newEntry);
                            optionalJar.write(input.readAllBytes());
                            optionalJar.closeEntry();
                        }
                    }
                }
                catch (IOException ex)
                {
                    // ignore artifacts that are not readable jars (for example sources or native
                    // classifier archives); they cannot contribute classes to the validation module
                }
            }
        }
    }

    private Set<String> missingDependencies(
        Path delegatePath,
        Path optionalModulesDir)
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(buffer);
        PrintStream nullOutput = new PrintStream(nullOutputStream());
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();
        jdeps.run(
            capture,
            nullOutput,
            "--missing-deps",
            "--module-path", optionalModulesDir.toString(),
            delegatePath.toString());

        Pattern missingPattern = Pattern.compile("->\\s+(\\S+)\\s+not found");
        Matcher missingMatcher = missingPattern.matcher(buffer.toString(UTF_8));
        Set<String> missing = new HashSet<>();
        while (missingMatcher.find())
        {
            missing.add(missingMatcher.group(1));
        }
        return missing;
    }

    private Set<String> jarPackages(
        Path path) throws IOException
    {
        Set<String> packages = new HashSet<>();
        try (JarFile jar = new JarFile(path.toFile()))
        {
            for (JarEntry entry : list(jar.entries()))
            {
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class"))
                {
                    int lastSlash = name.lastIndexOf('/');
                    if (lastSlash > 0)
                    {
                        packages.add(name.substring(0, lastSlash).replace('/', '.'));
                    }
                }
            }
        }
        return packages;
    }

    private Set<String> jarClasses(
        Path path) throws IOException
    {
        Set<String> classes = new HashSet<>();
        try (JarFile jar = new JarFile(path.toFile()))
        {
            for (JarEntry entry : list(jar.entries()))
            {
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class") && !name.endsWith(MODULE_INFO_CLASS_FILENAME))
                {
                    classes.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                }
            }
        }
        return classes;
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

                List<String> args = new ArrayList<>();
                if (atLeastVersion(javac, 21))
                {
                    args.add("-proc:none");
                }

                args.add("-d");
                args.add(generatedModuleDir.toString());

                args.add("--module-path");
                args.add(modulesDir.toString());

                args.add(generatedModuleInfo.toString());

                javac.run(
                    System.out,
                    System.err,
                    args.toArray(String[]::new));

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

        String compress = atLeastVersion(jlink, 21) ? "zip-6" : "2";

        List<String> extraModuleNames = new ArrayList<>();
        if (debug)
        {
            extraModuleNames.add("jdk.jdwp.agent");
        }
        if (instrument)
        {
            extraModuleNames.add("java.instrument");
        }

        extraModuleNames.add("java.management");
        extraModuleNames.add("jdk.management");

        Stream<String> moduleNames = Stream.concat(modules.stream().map(m -> m.name), extraModuleNames.stream());

        List<String> args = new ArrayList<>(Arrays.asList(
            "--module-path", modulesDir.toString(),
            "--output", imageDir.toString(),
            "--no-header-files",
            "--no-man-pages",
            "--compress", compress,
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
            "if [ -n \"$ZILLA_INCUBATOR_ENABLED\" ]; then",
            "JAVA_OPTIONS=\"$JAVA_OPTIONS -Dzilla.incubator.enabled=$ZILLA_INCUBATOR_ENABLED\"",
            "fi",
            "ZILLA_DIRECTORY=\"${0%/*}\"",
            "JAVA_OPTIONS=\"$JAVA_OPTIONS -Dzilla.directory=$ZILLA_DIRECTORY\"",
            "JAVA_OPTIONS=\"$JAVA_OPTIONS --add-opens java.base/jdk.internal.misc=ALL-UNNAMED " +
                "--add-opens java.base/jdk.internal.misc=org.agrona " +
                "--enable-native-access=io.aklivity.zilla.runtime.common.agrona " +
                "--sun-misc-unsafe-memory-access=%s\"".formatted(unsafeMemoryAccess),
            String.format(String.join(" ", Arrays.asList(
                    "exec $ZILLA_DIRECTORY/%s/bin/java",
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

    private static boolean atLeastVersion(
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

    private static Map<String, String> initDefaultRealms()
    {
        return singletonMap("maven.pkg.github.com", "GitHub Package Registry");
    }
}
