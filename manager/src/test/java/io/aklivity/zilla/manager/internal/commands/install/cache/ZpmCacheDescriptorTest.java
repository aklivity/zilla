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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.IntStream;

import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;

public class ZpmCacheDescriptorTest
{
    private static final int SIBLINGS = 6;
    private static final int PARENT_RESPONSE_DELAY_MILLIS = 300;
    private static final String PARENT_POM_PATH = "/test/parent/1.0/parent-1.0.pom";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final AtomicInteger parentRequests = new AtomicInteger();

    private ExecutorService executor;
    private HttpServer remote;
    private Path remoteDir;
    private Path cacheDir;

    @Before
    public void setUp() throws IOException
    {
        remoteDir = folder.newFolder("remote").toPath();
        cacheDir = folder.newFolder("cache").toPath();

        executor = Executors.newCachedThreadPool();
        remote = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        remote.setExecutor(executor);
        remote.createContext("/", exchange ->
        {
            String path = exchange.getRequestURI().getPath();
            Path file = remoteDir.resolve(path.substring(1));
            if (PARENT_POM_PATH.equals(path))
            {
                parentRequests.incrementAndGet();
                try
                {
                    Thread.sleep(PARENT_RESPONSE_DELAY_MILLIS);
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }

            if (Files.isRegularFile(file))
            {
                byte[] body = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            else
            {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        remote.start();
    }

    @After
    public void tearDown()
    {
        remote.stop(0);
        executor.shutdownNow();
    }

    @Test
    public void shouldTransferSharedParentOnceWhenResolvingSiblingDescriptors() throws IOException
    {
        writePom("parent", "<packaging>pom</packaging>");
        List<ZpmDependency> dependencies = IntStream.range(0, SIBLINGS)
            .mapToObj(i -> String.format("lib%d", i))
            .peek(this::writeSibling)
            .map(artifactId -> ZpmDependency.of("test", artifactId, "1.0"))
            .collect(toList());

        ZpmCache cache = newCache(false);
        List<ZpmArtifact> resolved = cache.resolve(null, dependencies);

        assertThat(parentRequests.get(), equalTo(1));
        assertThat(artifactIds(resolved), hasItem("lib0"));
        assertThat(artifactIds(resolved), hasItem(String.format("lib%d", SIBLINGS - 1)));
    }

    @Test
    public void shouldFailWhenParentDescriptorIsAbsent() throws IOException
    {
        writePom("top", """
            <dependencies>
              <dependency>
                <groupId>test</groupId>
                <artifactId>lib</artifactId>
                <version>1.0</version>
              </dependency>
            </dependencies>
            """);
        writePom("lib", """
            <parent>
              <groupId>test</groupId>
              <artifactId>parent</artifactId>
              <version>1.0</version>
            </parent>
            """);
        writeJar("top");
        writeJar("lib");

        ZpmCache cache = newCache(true);

        assertThrows(RuntimeException.class, () -> cache.resolve(null, List.of(ZpmDependency.of("test", "top", "1.0"))));
    }

    @Test
    public void shouldFailWhenTransitiveDescriptorIsInvalid() throws IOException
    {
        writePom("top", """
            <dependencies>
              <dependency>
                <groupId>test</groupId>
                <artifactId>lib</artifactId>
                <version>1.0</version>
              </dependency>
            </dependencies>
            """);
        writePom("lib", """
            <dependencies>
              <dependency>
                <groupId>test</groupId>
                <artifactId>codec</artifactId>
              </dependency>
            </dependencies>
            """);
        writePom("codec", "");
        writeJar("top");
        writeJar("lib");
        writeJar("codec");

        ZpmCache cache = newCache(true);

        assertThrows(RuntimeException.class, () -> cache.resolve(null, List.of(ZpmDependency.of("test", "top", "1.0"))));
    }

    @Test
    public void shouldFailWhenTransitiveDescriptorIsAbsent() throws IOException
    {
        writePom("lib", """
            <dependencies>
              <dependency>
                <groupId>test</groupId>
                <artifactId>codec</artifactId>
                <version>1.0</version>
              </dependency>
            </dependencies>
            """);
        writeJar("lib");
        writeJar("codec");

        ZpmCache cache = newCache(true);

        assertThrows(RuntimeException.class, () -> cache.resolve(null, List.of(ZpmDependency.of("test", "lib", "1.0"))));
    }

    @Test
    public void shouldResolveTransitiveDependencies() throws IOException
    {
        writePom("lib", """
            <dependencies>
              <dependency>
                <groupId>test</groupId>
                <artifactId>codec</artifactId>
                <version>1.0</version>
              </dependency>
            </dependencies>
            """);
        writePom("codec", "");
        writeJar("lib");
        writeJar("codec");

        ZpmCache cache = newCache(true);
        List<ZpmArtifact> resolved = cache.resolve(null, List.of(ZpmDependency.of("test", "lib", "1.0")));

        assertThat(artifactIds(resolved), containsInAnyOrder("lib", "codec"));
    }

    private ZpmCache newCache(
        boolean local)
    {
        String location = local
            ? remoteDir.toUri().toString()
            : String.format("http://localhost:%d/", remote.getAddress().getPort());
        List<RemoteRepository> repositories = List.of(new RemoteRepository.Builder("remote", "default", location).build());
        return new ZpmCache(repositories, local, cacheDir, new ConsoleLogger(LEVEL_DISABLED, "test"));
    }

    private void writeSibling(
        String artifactId)
    {
        try
        {
            writePom(artifactId, """
                <parent>
                  <groupId>test</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0</version>
                </parent>
                """);
            writeJar(artifactId);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private void writePom(
        String artifactId,
        String body) throws IOException
    {
        Path dir = Files.createDirectories(remoteDir.resolve(String.format("test/%s/1.0", artifactId)));
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
        Path dir = Files.createDirectories(remoteDir.resolve(String.format("test/%s/1.0", artifactId)));
        Path jar = dir.resolve(String.format("%s-1.0.jar", artifactId));
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
