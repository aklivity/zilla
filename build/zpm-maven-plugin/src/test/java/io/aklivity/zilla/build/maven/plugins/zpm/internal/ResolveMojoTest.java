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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.spi.ToolProvider;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ResolveMojoTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private ResolveMojo mojo;

    @Before
    public void setUp() throws Exception
    {
        File template = folder.newFile("zpm.json.template");
        Files.writeString(template.toPath(), """
            {
              "repositories": [],
              "dependencies": [ "test:declared", "test:${EXTRA}" ]
            }
            """, UTF_8);

        MavenProject project = new MavenProject();
        project.setArtifactId("docker-image");
        project.setArtifacts(Set.of(artifact("test", "declared")));

        mojo = new ResolveMojo();
        mojo.project = project;
        mojo.plugin = new PluginDescriptor();
        mojo.execution = new MojoExecution(new MojoDescriptor(), "default");
        mojo.template = template;
        mojo.properties = Map.of("EXTRA", "undeclared");
        mojo.workDirectory = folder.getRoot().toPath().resolve("zpm").toFile();
        mojo.repositoryDirectory = folder.getRoot().toPath().resolve("zpm/repository").toFile();
        mojo.localRepository = folder.newFolder("local");
    }

    @Test
    public void shouldSkip() throws Exception
    {
        mojo.skip = true;

        mojo.execute();

        assertThat(mojo.workDirectory, not(anExistingFile()));
    }

    @Test
    public void shouldRejectUndeclaredDependency()
    {
        MojoFailureException ex = assertThrows(MojoFailureException.class, mojo::execute);

        assertThat(ex.getMessage(), containsString("test:undeclared"));
    }

    @Test
    public void shouldResolveWithManager() throws Exception
    {
        mojo.properties = Map.of("EXTRA", "declared");
        mojo.offline = true;
        mojo.plugin.setArtifacts(List.of(manager()));

        mojo.execute();

        Path executionDir = mojo.workDirectory.toPath().resolve("default");
        assertThat(Files.readString(executionDir.resolve("config/zpm.json"), UTF_8),
            containsString("\"test:declared\", \"test:declared\""));
        assertThat(Files.readString(mojo.repositoryDirectory.toPath().resolve("args.txt"), UTF_8), equalTo(String.join(" ",
            "resolve",
            "--config-directory", executionDir.resolve("config").toString(),
            "--output-directory", executionDir.resolve("zpm").toString(),
            "--local-repository", mojo.localRepository.getAbsolutePath(),
            "--repository-directory", mojo.repositoryDirectory.getAbsolutePath(),
            "--exclude-remote-repositories")));
        assertThat(mojo.repositoryDirectory.toPath().resolve("io/aklivity/zilla/manager/1.0/manager-1.0.jar").toFile(),
            anExistingFile());
    }

    private Artifact manager() throws IOException
    {
        Path sources = folder.newFolder().toPath();
        Path classes = folder.newFolder().toPath();
        Path source = sources.resolve("ZpmMain.java");
        Files.writeString(source, """
            package io.aklivity.zilla.manager.internal;

            public final class ZpmMain
            {
                public static void main(String[] args) throws Exception
                {
                    java.nio.file.Path repository = java.nio.file.Path.of(args[java.util.List.of(args)
                        .indexOf("--repository-directory") + 1]);
                    java.nio.file.Files.createDirectories(repository);
                    java.nio.file.Files.writeString(repository.resolve("args.txt"), String.join(" ", args));
                }
            }
            """, UTF_8);
        ToolProvider.findFirst("javac").get().run(System.out, System.err, "-d", classes.toString(), source.toString());

        String entry = "io/aklivity/zilla/manager/internal/ZpmMain.class";
        File jar = folder.newFile("manager.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath())))
        {
            out.putNextEntry(new JarEntry(entry));
            out.write(Files.readAllBytes(classes.resolve(entry)));
            out.closeEntry();
        }

        Artifact manager = new DefaultArtifact("io.aklivity.zilla", "manager", "1.0", "runtime", "jar", null,
            new DefaultArtifactHandler("jar"));
        manager.setFile(jar);
        return manager;
    }

    private static Artifact artifact(
        String groupId,
        String artifactId)
    {
        return new DefaultArtifact(groupId, artifactId, "1.0", "runtime", "jar", null, new DefaultArtifactHandler("jar"));
    }
}
