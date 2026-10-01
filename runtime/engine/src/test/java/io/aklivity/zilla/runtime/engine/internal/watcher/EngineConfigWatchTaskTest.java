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
package io.aklivity.zilla.runtime.engine.internal.watcher;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_WATCH;
import static io.aklivity.zilla.runtime.filesystem.http.HttpFilesystemEnvironment.POLL_INTERVAL_PROPERTY_NAME;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.internal.event.EngineEventContext;

public class EngineConfigWatchTaskTest
{
    private static final long TIMEOUT_SECONDS = 60L;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void shouldApplyChangeAfterUnchangedRewrite() throws Exception
    {
        Path configPath = temp.getRoot().toPath().toRealPath().resolve("zilla.yaml");
        write(configPath, "name: before");

        try (TextWatchTask task = new TextWatchTask(configPath))
        {
            task.watch(configPath.getFileName().toString());
            task.submit();
            assertEquals("name: before", task.applied.poll(TIMEOUT_SECONDS, SECONDS));
            task.observed.drainPermits();

            write(configPath, "name: before");
            assertTrue(task.observed.tryAcquire(TIMEOUT_SECONDS, SECONDS));

            write(configPath, "name: after");
            assertEquals("name: after", task.applied.poll(TIMEOUT_SECONDS, SECONDS));
        }
    }

    @Test
    public void shouldApplyHttpChangesAfterReload() throws Exception
    {
        try (ConfigServer server = new ConfigServer("name: first"))
        {
            URI configURI = URI.create(String.format("http://localhost:%d/zilla.yaml", server.port()));

            try (FileSystem fs = FileSystems.newFileSystem(configURI, Map.of(POLL_INTERVAL_PROPERTY_NAME, "PT0S"));
                 TextWatchTask task = new TextWatchTask(fs.getPath(configURI.toString())))
            {
                task.submit();
                assertEquals("name: first", task.applied.poll(TIMEOUT_SECONDS, SECONDS));

                server.update("name: second");
                assertEquals("name: second", task.applied.poll(TIMEOUT_SECONDS, SECONDS));

                server.update("name: third");
                assertEquals("name: third", task.applied.poll(TIMEOUT_SECONDS, SECONDS));
            }
        }
    }

    private static void write(
        Path path,
        String text) throws IOException
    {
        Path staged = Files.writeString(path.resolveSibling(path.getFileName() + ".staged"), text);
        Files.move(staged, path, ATOMIC_MOVE);
    }

    private static final class ConfigServer implements AutoCloseable
    {
        private final HttpServer server;

        private String text;
        private int version;
        private boolean closed;

        ConfigServer(
            String text) throws IOException
        {
            this.text = text;
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            this.server.createContext("/zilla.yaml", this::handle);
            this.server.setExecutor(Executors.newCachedThreadPool());
            this.server.start();
        }

        int port()
        {
            return server.getAddress().getPort();
        }

        synchronized void update(
            String text)
        {
            this.text = text;
            this.version++;
            notifyAll();
        }

        @Override
        public void close()
        {
            synchronized (this)
            {
                closed = true;
                notifyAll();
            }
            server.stop(0);
        }

        private void handle(
            HttpExchange exchange) throws IOException
        {
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            boolean longPoll = exchange.getRequestHeaders().containsKey("Prefer");

            String etag;
            byte[] body;

            synchronized (this)
            {
                long deadline = System.currentTimeMillis() + SECONDS.toMillis(TIMEOUT_SECONDS);
                long remaining = deadline - System.currentTimeMillis();
                while (longPoll && !closed && Integer.toString(version).equals(ifNoneMatch) && remaining > 0L)
                {
                    try
                    {
                        wait(remaining);
                        remaining = deadline - System.currentTimeMillis();
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                etag = Integer.toString(version);
                body = text.getBytes(UTF_8);
            }

            exchange.getResponseHeaders().add("Etag", etag);

            if (etag.equals(ifNoneMatch))
            {
                exchange.sendResponseHeaders(HTTP_NOT_MODIFIED, -1);
            }
            else
            {
                exchange.sendResponseHeaders(HTTP_OK, body.length);
                try (OutputStream out = exchange.getResponseBody())
                {
                    out.write(body);
                }
            }

            exchange.close();
        }
    }

    private static final class TextWatchTask extends EngineConfigWatchTask
    {
        private static final Properties WATCH_ENABLED = new Properties();

        static
        {
            WATCH_ENABLED.setProperty(ENGINE_CONFIG_WATCH.name(), "true");
        }

        private final Path configPath;
        private final Semaphore observed;
        private final BlockingQueue<String> applied;

        private volatile String currentText;

        TextWatchTask(
            Path configPath)
        {
            super(new EngineConfiguration(WATCH_ENABLED), mock(EngineEventContext.class), configPath);
            this.configPath = configPath;
            this.observed = new Semaphore(0);
            this.applied = new LinkedBlockingQueue<>();
        }

        @Override
        protected void onPathChanged(
            Path watchedPath)
        {
            try
            {
                String newText = Files.readString(configPath);
                if (!newText.equals(currentText))
                {
                    currentText = newText;
                    applied.add(newText);
                }
            }
            catch (IOException ex)
            {
                rethrowUnchecked(ex);
            }

            observed.release();
        }
    }
}
