/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.filesystem.http;

import static io.aklivity.zilla.runtime.filesystem.http.HttpFilesystemEnvironment.POLL_INTERVAL_PROPERTY_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class HttpFileSystemWatchFailureTest
{
    private static final byte[] BODY = "Hello World!".getBytes(UTF_8);
    private static final int DROPPED_WATCHES = 10;

    @Test
    public void shouldKeepContentWhenWatchConnectionDrops() throws Exception
    {
        AtomicInteger watches = new AtomicInteger();
        CountDownLatch rewatched = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(executor);
        server.createContext("/hello.txt", exchange ->
        {
            if (exchange.getRequestHeaders().getFirst("Prefer") == null)
            {
                respond(exchange);
            }
            else if (watches.incrementAndGet() <= DROPPED_WATCHES)
            {
                exchange.close();
            }
            else
            {
                rewatched.countDown();
                await(release);
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
            }
        });
        server.start();

        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of(POLL_INTERVAL_PROPERTY_NAME, "PT0S")))
        {
            Path path = fs.getPath(uri.toString());
            assertThat(Files.readString(path), equalTo("Hello World!"));

            try (WatchService watcher = fs.newWatchService())
            {
                path.register(watcher);

                assertThat(rewatched.await(10, SECONDS), equalTo(true));
                WatchKey key = watcher.poll(500, MILLISECONDS);

                assertThat(key, nullValue());
                assertThat(Files.readString(path), equalTo("Hello World!"));
            }
        }
        finally
        {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static void respond(
        HttpExchange exchange) throws IOException
    {
        exchange.getResponseHeaders().add("Etag", "AAAAAAA");
        exchange.sendResponseHeaders(200, BODY.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(BODY);
        }
    }

    private static void await(
        CountDownLatch latch)
    {
        try
        {
            latch.await(10, SECONDS);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
