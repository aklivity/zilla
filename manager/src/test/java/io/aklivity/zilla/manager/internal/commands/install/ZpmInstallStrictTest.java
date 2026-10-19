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
import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.rvesse.airline.Cli;

import io.aklivity.zilla.manager.internal.ZpmCli;

public class ZpmInstallStrictTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path configDir;

    @Before
    public void setUp() throws Exception
    {
        Path repositoryDir = folder.newFolder("repository").toPath();
        new ZpmTestRepository(repositoryDir)
            .pom("auto", """
                <dependencies>
                  <dependency>
                    <groupId>test</groupId>
                    <artifactId>absent</artifactId>
                    <version>1.0</version>
                    <optional>true</optional>
                  </dependency>
                </dependencies>
                """)
            .jar("auto",
                "test.auto.Auto", "package test.auto; public class Auto { public test.absent.Absent absent; }",
                "test.absent.Absent", "package test.absent; public class Absent { }");

        configDir = folder.newFolder("config").toPath();
        Files.writeString(configDir.resolve("zpm.json"), String.format("""
            {
              "repositories": [ "%s" ],
              "dependencies": [ "test:auto:1.0" ]
            }
            """, repositoryDir.toUri()), UTF_8);
    }

    @Test
    public void shouldFailStrictInstallWhenDelegateReferencesMissingDependency()
    {
        Path outputDir = folder.getRoot().toPath().resolve("zpm");

        String[] args =
        {
            "install",
            "--config-directory", configDir.toString(),
            "--lock-directory", outputDir.resolve("lock").toString(),
            "--output-directory", outputDir.toString(),
            "--launcher-directory", outputDir.toString(),
            "--exclude-local-repository",
            "--strict",
            "--silent"
        };

        Runnable install = new Cli<Runnable>(ZpmCli.class).parse(args);

        assertThrows(RuntimeException.class, install::run);
    }
}
