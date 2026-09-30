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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.rvesse.airline.Cli;

import io.aklivity.zilla.manager.internal.ZpmCli;

public class ZpmResolveTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path remoteDir;
    private Path localDir;
    private Path configDir;

    @Before
    public void setUp() throws Exception
    {
        remoteDir = folder.newFolder("remote").toPath();
        new ZpmTestRepository(remoteDir)
            .pom("parent", "<packaging>pom</packaging>")
            .pom("auto", """
                <parent>
                  <groupId>test</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                </parent>
                <dependencies>
                  <dependency>
                    <groupId>test</groupId>
                    <artifactId>codec</artifactId>
                    <version>1.0</version>
                    <optional>true</optional>
                  </dependency>
                  <dependency>
                    <groupId>test</groupId>
                    <artifactId>absent</artifactId>
                    <version>1.0</version>
                    <optional>true</optional>
                  </dependency>
                </dependencies>
                """)
            .pom("codec", "")
            .jar("auto",
                "test.auto.Auto", "package test.auto; public class Auto { public test.codec.Codec codec; }",
                "test.codec.Codec", "package test.codec; public class Codec { }")
            .jar("codec",
                "test.codec.Codec", "package test.codec; public class Codec { }");

        localDir = folder.newFolder("local").toPath();

        configDir = folder.newFolder("config").toPath();
        Files.writeString(configDir.resolve("zpm.json"), String.format("""
            {
              "repositories": [ "%s" ],
              "dependencies": [ "test:auto:1.0" ]
            }
            """, remoteDir.toUri()), UTF_8);
    }

    @Test
    public void shouldExportResolvedAndOptionalDependencies()
    {
        Path repositoryDir = folder.getRoot().toPath().resolve("repository");

        resolve(repositoryDir);

        assertExported(repositoryDir);
        assertThat(repositoryDir.resolve("test/absent").toFile(), not(anExistingFile()));
    }

    @Test
    public void shouldExportFromLocalRepositoryCache() throws IOException
    {
        Path repositoryDir = folder.getRoot().toPath().resolve("repository");
        resolve(repositoryDir);

        deleteDirectories(repositoryDir);
        deleteDirectories(remoteDir);
        Files.createDirectories(remoteDir);

        resolve(repositoryDir);

        assertExported(repositoryDir);
    }

    private void resolve(
        Path repositoryDir)
    {
        String[] args =
        {
            "resolve",
            "--config-directory", configDir.toString(),
            "--output-directory", folder.getRoot().toPath().resolve("zpm").toString(),
            "--local-repository", localDir.toString(),
            "--repository-directory", repositoryDir.toString(),
            "--silent"
        };

        new Cli<Runnable>(ZpmCli.class).parse(args).run();
    }

    private static void assertExported(
        Path repositoryDir)
    {
        assertThat(repositoryDir.resolve("test/auto/1.0/auto-1.0.jar").toFile(), anExistingFile());
        assertThat(repositoryDir.resolve("test/auto/1.0/auto-1.0.pom").toFile(), anExistingFile());
        assertThat(repositoryDir.resolve("test/parent/1.0/parent-1.0.pom").toFile(), anExistingFile());
        assertThat(repositoryDir.resolve("test/codec/1.0/codec-1.0.jar").toFile(), anExistingFile());
    }

    private static void deleteDirectories(
        Path dir) throws IOException
    {
        try (Stream<Path> paths = Files.walk(dir))
        {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
