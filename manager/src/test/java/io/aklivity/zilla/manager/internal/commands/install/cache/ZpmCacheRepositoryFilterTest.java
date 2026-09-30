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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

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

public class ZpmCacheRepositoryFilterTest
{
    private static final String CONFIG_PROP_FILTER_GROUP_ID = "aether.remoteRepositoryFilter.groupId";
    private static final String CONFIG_PROP_FILTER_GROUP_ID_BASEDIR = "aether.remoteRepositoryFilter.groupId.basedir";

    private static final List<ZpmDependency> DEPENDENCIES = List.of(ZpmDependency.of("other", "lib", "1.0"));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final AtomicInteger requests = new AtomicInteger();

    private HttpServer filtered;
    private List<RemoteRepository> repositories;
    private Path cacheDir;
    private Path filterDir;

    @Before
    public void setUp() throws IOException
    {
        filtered = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        filtered.createContext("/", exchange ->
        {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        filtered.start();

        Path remoteDir = folder.newFolder("remote").toPath();
        Path libDir = Files.createDirectories(remoteDir.resolve("other/lib/1.0"));
        Files.writeString(libDir.resolve("lib-1.0.pom"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>other</groupId>
              <artifactId>lib</artifactId>
              <version>1.0</version>
            </project>
            """, UTF_8);
        Files.write(libDir.resolve("lib-1.0.jar"), new byte[0]);

        String filteredUrl = String.format("http://localhost:%d/", filtered.getAddress().getPort());
        repositories = List.of(
            new RemoteRepository.Builder("filtered", "default", filteredUrl).build(),
            new RemoteRepository.Builder("remote", "default", remoteDir.toUri().toString()).build());

        cacheDir = folder.newFolder("cache").toPath();
        filterDir = folder.newFolder("filters").toPath();
        Files.writeString(filterDir.resolve("groupId-filtered.txt"), "test.allowed\n", UTF_8);
    }

    @After
    public void tearDown()
    {
        System.clearProperty(CONFIG_PROP_FILTER_GROUP_ID);
        System.clearProperty(CONFIG_PROP_FILTER_GROUP_ID_BASEDIR);
        filtered.stop(0);
    }

    @Test
    public void shouldNotRequestUnlistedGroupFromFilteredRepository()
    {
        System.setProperty(CONFIG_PROP_FILTER_GROUP_ID, "true");
        System.setProperty(CONFIG_PROP_FILTER_GROUP_ID_BASEDIR, filterDir.toString());

        ZpmCache cache = new ZpmCache(repositories, false, cacheDir, new ConsoleLogger(LEVEL_DISABLED, "test"));

        assertThat(cache.resolve(List.of(), DEPENDENCIES), hasSize(1));
        assertThat(requests.get(), equalTo(0));
    }

    @Test
    public void shouldRequestFromEveryRepositoryWithoutFilter()
    {
        ZpmCache cache = new ZpmCache(repositories, false, cacheDir, new ConsoleLogger(LEVEL_DISABLED, "test"));

        assertThat(cache.resolve(List.of(), DEPENDENCIES), hasSize(1));
        assertThat(requests.get(), greaterThan(0));
    }
}
