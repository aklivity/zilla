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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.internal.event.EngineEventContext;

public class EngineConfigWatchTaskTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void shouldApplyChangeAfterUnchangedRewrite() throws Exception
    {
        Path configPath = temp.getRoot().toPath().toRealPath().resolve("zilla.yaml");
        write(configPath, "name: before");

        try (TextWatchTask task = new TextWatchTask(configPath))
        {
            task.submit();
            assertEquals("name: before", task.applied.poll(30, SECONDS));

            long deadline = System.nanoTime() + SECONDS.toNanos(30);
            while (task.changes.get() == 1 && System.nanoTime() < deadline)
            {
                write(configPath, "name: before");
                Thread.sleep(100);
            }
            assertTrue(task.changes.get() > 1);

            write(configPath, "name: after");

            assertEquals("name: after", task.applied.poll(30, SECONDS));
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
                assertEquals("name: first", task.applied.poll(30, SECONDS));

                server.update("name: second");
                assertEquals("name: second", task.applied.poll(30, SECONDS));

                server.update("name: third");
                assertEquals("name: third", task.applied.poll(30, SECONDS));
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
                long deadline = System.currentTimeMillis() + SECONDS.toMillis(30);
                while (longPoll && !closed && Integer.toString(version).equals(ifNoneMatch) &&
                    System.currentTimeMillis() < deadline)
                {
                    try
                    {
                        wait(100);
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
        private final Path configPath;
        private final AtomicInteger changes;
        private final BlockingQueue<String> applied;

        private String currentText;

        TextWatchTask(
            Path configPath)
        {
            super(new EngineConfiguration(), mock(EngineEventContext.class), configPath);
            this.configPath = configPath;
            this.changes = new AtomicInteger();
            this.applied = new LinkedBlockingQueue<>();
        }

        @Override
        protected void onPathChanged(
            Path watchedPath)
        {
            changes.incrementAndGet();

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
        }
    }
}
