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

import static java.nio.file.Files.createDirectories;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.repository.RemoteRepository;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.Required;

import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmArtifact;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmCache;
import io.aklivity.zilla.manager.internal.commands.install.cache.ZpmModule;
import io.aklivity.zilla.manager.internal.types.ZpmPathConverterProvider;

@Command(
    name = "resolve",
    description = "Resolve dependencies into a repository for a later offline install")
public final class ZpmResolve extends ZpmDependencyCommand
{
    @Option(name = {"--repository-directory"},
        description = "Repository directory to export resolved dependencies into",
        typeConverterProvider = ZpmPathConverterProvider.class)
    @Required
    public Path repositoryDir;

    @Option(name = {"--local-repository"},
        description = "Local Maven repository used to cache resolved dependencies",
        typeConverterProvider = ZpmPathConverterProvider.class)
    public Path localRepositoryDir = Paths.get(System.getProperty("user.home"), ".m2", "repository");

    @Override
    public void invoke()
    {
        ConsoleLogger logger = newLogger("ZpmResolve");

        try
        {
            Path zpmFile = configDir.resolve("zpm.json");

            logger.info(String.format("reading %s", zpmFile));
            ZpmConfiguration config = readOrDefaultConfig(zpmFile);

            List<RemoteRepository> remoteRepositories = remoteRepositories(config, null);
            ZpmCache cache = new ZpmCache(remoteRepositories, excludeRemoteRepos, localRepositoryDir, logger);

            long begin = System.nanoTime();
            logger.info("resolving dependencies");
            List<ZpmArtifact> artifacts = cache.resolve(config.imports, config.dependencies);
            logger.info(String.format("resolved %d dependencies in %s", artifacts.size(), elapsed(begin)));

            createDirectories(generatedDir);

            ZpmModule delegate = new ZpmModule();
            Collection<ZpmModule> modules = discoverModules(artifacts);
            migrateUnnamed(modules, delegate);
            generateSystemOnlyAutomatic(logger, modules);
            delegateAutomatic(modules, delegate);

            if (!delegate.depends.isEmpty())
            {
                begin = System.nanoTime();
                logger.info("resolving optional dependencies");
                List<ZpmArtifact> optional = cache.resolveOptional(config.imports, delegate.depends, artifacts);
                logger.info(String.format("resolved %d optional dependencies in %s", optional.size(), elapsed(begin)));
            }

            begin = System.nanoTime();
            logger.info(String.format("exporting resolved dependencies to %s", repositoryDir));
            cache.export(repositoryDir);
            logger.info(String.format("exported resolved dependencies in %s", elapsed(begin)));
        }
        catch (Exception ex)
        {
            logger.error(String.format("Error: %s", ex.getMessage()));
            throw new RuntimeException(ex);
        }
    }
}
