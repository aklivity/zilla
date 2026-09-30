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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.codehaus.plexus.logging.Logger.LEVEL_DISABLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;

public class ZpmCacheOptionalTest
{
    private static final List<ZpmDependency> DEPENDENCIES = List.of(
        ZpmDependency.of("test", "auto", "1.0"),
        ZpmDependency.of("test", "named", "1.0"));

    private static final List<ZpmArtifactId> DELEGATED = List.of(new ZpmArtifactId("test", "auto", "1.0"));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path localDir;

    @Before
    public void setUp() throws IOException
    {
        localDir = folder.newFolder("local").toPath();

        writePom("parent", "<packaging>pom</packaging>");
        writePom("auto", """
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
                <artifactId>annotations</artifactId>
                <version>1.0</version>
                <scope>provided</scope>
              </dependency>
              <dependency>
                <groupId>test</groupId>
                <artifactId>absent</artifactId>
                <version>1.0</version>
                <optional>true</optional>
              </dependency>
            </dependencies>
            """);
        writePom("named", """
            <dependencies>
              <dependency>
                <groupId>test</groupId>
                <artifactId>named.spec</artifactId>
                <version>1.0</version>
                <scope>provided</scope>
              </dependency>
            </dependencies>
            """);
        writePom("codec", "");
        writePom("annotations", "");

        writeJar("auto");
        writeJar("named");
        writeJar("codec");
        writeJar("annotations");
    }

    @Test
    public void shouldResolveOptionalTreeOfDelegatedArtifactsOnly()
    {
        ZpmCache cache = newCache(localDir, folder.getRoot().toPath().resolve("cache"));

        List<ZpmArtifact> resolved = cache.resolve(null, DEPENDENCIES);
        List<ZpmArtifact> optional = cache.resolveOptional(null, DELEGATED, resolved);

        assertThat(artifactIds(resolved), containsInAnyOrder("auto", "named"));
        assertThat(artifactIds(optional), hasItems("auto", "codec", "annotations"));
        assertThat(artifactIds(optional), not(hasItems("named.spec")));
        assertThat(artifactIds(optional), not(hasItems("named")));
    }

    @Test
    public void shouldExportSelfContainedRepository() throws IOException
    {
        ZpmCache cache = newCache(localDir, folder.getRoot().toPath().resolve("cache"));
        List<ZpmArtifact> resolved = cache.resolve(null, DEPENDENCIES);
        cache.resolveOptional(null, DELEGATED, resolved);

        Path exportDir = folder.newFolder("export").toPath();
        cache.export(exportDir);

        assertThat(exportDir.resolve("test/auto/1.0/auto-1.0.jar").toFile(), anExistingFile());
        assertThat(exportDir.resolve("test/auto/1.0/auto-1.0.pom").toFile(), anExistingFile());
        assertThat(exportDir.resolve("test/parent/1.0/parent-1.0.pom").toFile(), anExistingFile());
        assertThat(exportDir.resolve("test/codec/1.0/codec-1.0.jar").toFile(), anExistingFile());

        ZpmCache exported = newCache(exportDir, folder.getRoot().toPath().resolve("exported-cache"));
        List<ZpmArtifact> reresolved = exported.resolve(null, DEPENDENCIES);
        List<ZpmArtifact> reoptional = exported.resolveOptional(null, DELEGATED, reresolved);

        assertThat(artifactIds(reresolved), containsInAnyOrder("auto", "named"));
        assertThat(artifactIds(reoptional), hasItems("auto", "codec", "annotations"));
    }

    private ZpmCache newCache(
        Path repositoryDir,
        Path cacheDir)
    {
        List<RemoteRepository> repositories =
            List.of(new RemoteRepository.Builder("local", "default", repositoryDir.toUri().toString()).build());
        return new ZpmCache(repositories, true, cacheDir, new ConsoleLogger(LEVEL_DISABLED, "test"));
    }

    private void writePom(
        String artifactId,
        String body) throws IOException
    {
        Path dir = Files.createDirectories(localDir.resolve(String.format("test/%s/1.0", artifactId)));
        Files.writeString(dir.resolve(String.format("%s-1.0.pom", artifactId)), String.format("""
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>%s</artifactId>
              <version>1.0</version>
            %s
            </project>
            """, artifactId, body), UTF_8);
    }

    private void writeJar(
        String artifactId) throws IOException
    {
        Path jar = localDir.resolve(String.format("test/%1$s/1.0/%1$s-1.0.jar", artifactId));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), new Manifest()))
        {
            out.flush();
        }
    }

    private static List<String> artifactIds(
        List<ZpmArtifact> artifacts)
    {
        return artifacts.stream().map(a -> a.id.artifact).collect(toList());
    }
}
