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
import static org.codehaus.plexus.logging.Logger.LEVEL_DISABLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;

public class ZpmCacheTest
{
    private static final List<ZpmDependency> IMPORTS = List.of(ZpmDependency.of("test", "bom", "1.0"));
    private static final List<ZpmDependency> DEPENDENCIES = List.of(ZpmDependency.of("test", "lib", null));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final AtomicInteger requests = new AtomicInteger();

    private HttpServer remote;
    private List<RemoteRepository> repositories;
    private Path cacheDir;

    @Before
    public void setUp() throws IOException
    {
        remote = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        remote.createContext("/", exchange ->
        {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        remote.start();

        Path localDir = folder.newFolder("local").toPath();
        Path bomDir = Files.createDirectories(localDir.resolve("test/bom/1.0"));
        Files.writeString(bomDir.resolve("bom-1.0.pom"), String.format("""
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>bom</artifactId>
              <version>1.0</version>
              <packaging>pom</packaging>
              <repositories>
                <repository>
                  <id>remote</id>
                  <url>http://localhost:%d/</url>
                </repository>
              </repositories>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>test</groupId>
                    <artifactId>lib</artifactId>
                    <version>1.0</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            """, remote.getAddress().getPort()), UTF_8);

        repositories = List.of(new RemoteRepository.Builder("local", "default", localDir.toUri().toString()).build());
        cacheDir = folder.newFolder("cache").toPath();
    }

    @After
    public void tearDown()
    {
        remote.stop(0);
    }

    @Test
    public void shouldNotResolveFromImportRepositoriesWhenExcludingRemote()
    {
        ZpmCache cache = new ZpmCache(repositories, true, cacheDir, new ConsoleLogger(LEVEL_DISABLED, "test"));

        assertThrows(RuntimeException.class, () -> cache.resolve(IMPORTS, DEPENDENCIES));
        assertThat(cache.resolveOptional(IMPORTS, DEPENDENCIES), empty());
        assertThat(requests.get(), equalTo(0));
    }

    @Test
    public void shouldResolveFromImportRepositoriesWhenIncludingRemote()
    {
        ZpmCache cache = new ZpmCache(repositories, false, cacheDir, new ConsoleLogger(LEVEL_DISABLED, "test"));

        assertThrows(RuntimeException.class, () -> cache.resolve(IMPORTS, DEPENDENCIES));
        assertThat(requests.get(), greaterThan(0));
    }
}
