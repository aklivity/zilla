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
package io.aklivity.zilla.build.maven.plugins.zpm.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PREPARE_PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(
    name = "resolve",
    defaultPhase = PREPARE_PACKAGE,
    requiresDependencyResolution = RUNTIME,
    threadSafe = true)
public final class ResolveMojo extends AbstractMojo
{
    private static final String MANAGER_KEY = "io.aklivity.zilla:manager";

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${plugin}", readonly = true)
    PluginDescriptor plugin;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    MojoExecution execution;

    @Parameter(defaultValue = "${project.basedir}/src/main/docker/zpm.json.template")
    File template;

    @Parameter
    Map<String, String> properties = Map.of();

    @Parameter(defaultValue = "${project.build.directory}/zpm/repository")
    File repositoryDirectory;

    @Parameter(defaultValue = "${project.build.directory}/zpm")
    File workDirectory;

    @Parameter(defaultValue = "${settings.localRepository}", readonly = true)
    File localRepository;

    @Parameter(defaultValue = "${session.offline}", readonly = true)
    boolean offline;

    @Parameter(property = "zpm.skip", defaultValue = "false")
    boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if (skip)
        {
            getLog().info("Skipping zpm resolve");
        }
        else
        {
            try
            {
                String config = ZpmTemplate.substitute(Files.readString(template.toPath(), UTF_8), properties);

                validateDependencies(config);

                Path executionDir = workDirectory.toPath().resolve(execution.getExecutionId());
                Path configDir = Files.createDirectories(executionDir.resolve("config"));
                Files.writeString(configDir.resolve("zpm.json"), config, UTF_8);

                new ZpmLauncher(managerJar()).run(resolveArgs(configDir, executionDir));
            }
            catch (MojoFailureException | MojoExecutionException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                throw new MojoExecutionException(String.format("zpm resolve failed: %s", ex.getMessage()), ex);
            }
        }
    }

    private void validateDependencies(
        String config) throws MojoFailureException
    {
        Set<String> declared = project.getArtifacts().stream()
            .map(a -> String.format("%s:%s", a.getGroupId(), a.getArtifactId()))
            .collect(toSet());

        List<String> undeclared = ZpmTemplate.dependencies(config).stream()
            .filter(d -> !declared.contains(d))
            .toList();

        if (!undeclared.isEmpty())
        {
            throw new MojoFailureException(String.format(
                "%s lists dependencies not declared by %s: %s", template, project.getArtifactId(), undeclared));
        }
    }

    private List<String> resolveArgs(
        Path configDir,
        Path executionDir)
    {
        List<String> args = new ArrayList<>();
        args.add("resolve");
        args.add("--config-directory");
        args.add(configDir.toString());
        args.add("--output-directory");
        args.add(executionDir.resolve("zpm").toString());
        args.add("--local-repository");
        args.add(localRepository.getAbsolutePath());
        args.add("--repository-directory");
        args.add(repositoryDirectory.getAbsolutePath());
        if (offline)
        {
            args.add("--exclude-remote-repositories");
        }
        if (getLog().isDebugEnabled())
        {
            args.add("--verbose");
        }
        return args;
    }

    private File managerJar() throws MojoExecutionException
    {
        Artifact manager = plugin.getArtifactMap().get(MANAGER_KEY);
        if (manager == null || manager.getFile() == null)
        {
            throw new MojoExecutionException(String.format("Unable to locate %s plugin dependency", MANAGER_KEY));
        }
        return manager.getFile();
    }
}
