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

import static org.codehaus.plexus.logging.Logger.LEVEL_DISABLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZpmCacheExportTest
{
    private static final int THREADS = 8;
    private static final int ARTIFACTS_PER_THREAD = 2000;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldExportArtifactsResolvedConcurrently() throws Exception
    {
        Path localDir = folder.newFolder("local").toPath();
        Path exportDir = folder.newFolder("export").toPath();

        ZpmCache cache = new ZpmCache(List.of(), true, localDir, new ConsoleLogger(LEVEL_DISABLED, "test"));
        RepositoryListener listener = cache.new ZpmConsoleRepositoryListener();
        RepositorySystemSession session = new DefaultRepositorySystemSession(h -> false);

        List<List<RepositoryEvent>> eventsByThread = new ArrayList<>();
        for (int t = 0; t < THREADS; t++)
        {
            List<RepositoryEvent> events = new ArrayList<>();
            for (int i = 0; i < ARTIFACTS_PER_THREAD; i++)
            {
                String artifactId = String.format("artifact-%d-%d", t, i);
                Path path = Files.createDirectories(localDir.resolve("test").resolve(artifactId).resolve("1.0"))
                    .resolve(artifactId + "-1.0.pom");
                Files.createFile(path);
                events.add(new RepositoryEvent.Builder(session, EventType.ARTIFACT_RESOLVED)
                    .setArtifact(new DefaultArtifact("test", artifactId, "pom", "1.0"))
                    .setPath(path)
                    .build());
            }
            eventsByThread.add(events);
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try
        {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (List<RepositoryEvent> events : eventsByThread)
            {
                futures.add(executor.submit(() ->
                {
                    start.await();
                    events.forEach(listener::artifactResolved);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures)
            {
                future.get();
            }
        }
        finally
        {
            executor.shutdownNow();
        }

        cache.export(exportDir);

        assertThat(countFiles(exportDir), equalTo((long) THREADS * ARTIFACTS_PER_THREAD));
    }

    private static long countFiles(
        Path dir) throws IOException
    {
        try (Stream<Path> paths = Files.walk(dir))
        {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}
