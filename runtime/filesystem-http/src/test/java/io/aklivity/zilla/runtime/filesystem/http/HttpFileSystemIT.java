/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.runtime.filesystem.http.internal.HttpFileSystemConfiguration.AUTHORIZATION_PROPERTY_NAME;
import static io.aklivity.zilla.runtime.filesystem.http.internal.HttpFileSystemConfiguration.POLL_INTERVAL_PROPERTY_NAME;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.rules.RuleChain.outerRule;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class HttpFileSystemIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/filesystem/http/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/read.success/server",
    })
    public void shouldReadString() throws Exception
    {
        // GIVEN
        URI helloURI = URI.create("http://localhost:8080/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(helloURI, Map.of()))
        {
            Path helloPath = fs.getPath(helloURI.toString());

            // WHEN
            k3po.start();
            String helloBody = Files.readString(helloPath);
            k3po.finish();

            // THEN
            assertThat(helloBody, equalTo("Hello World!"));
        }
    }

    @Test
    @Specification({
        "${app}/read.success.etag.not.modified/server",
    })
    public void shouldReadStringEtagNotModified() throws Exception
    {
        // GIVEN
        URI helloURI = URI.create("http://localhost:8080/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(helloURI, Map.of()))
        {
            Path helloPath = fs.getPath(helloURI.toString());

            // WHEN
            k3po.start();
            String helloBody1 = Files.readString(helloPath);
            String helloBody2 = Files.readString(helloPath);
            k3po.finish();

            // THEN
            assertThat(helloBody1, equalTo("Hello World!"));
            assertThat(helloBody2, equalTo("Hello World!"));
        }
    }

    @Test
    @Specification({
        "${app}/read.success.etag.modified/server",
    })
    public void shouldReadStringEtagModified() throws Exception
    {
        // GIVEN
        URI helloURI = URI.create("http://localhost:8080/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(helloURI, Map.of()))
        {
            Path helloPath = fs.getPath(helloURI.toString());

            // WHEN
            k3po.start();
            String helloBody1 = Files.readString(helloPath);
            String helloBody2 = Files.readString(helloPath);
            k3po.finish();

            // THEN
            assertThat(helloBody1, equalTo("Hello World!"));
            assertThat(helloBody2, equalTo("Hello Universe!"));
        }
    }

    @Test
    @Specification({
        "${app}/read.notfound/server",
    })
    public void shouldReadStringNotFound() throws Exception
    {
        // GIVEN
        URI notFoundURI = URI.create("http://localhost:8080/notfound.txt");
        try (FileSystem fs = FileSystems.newFileSystem(notFoundURI, Map.of()))
        {
            Path notFoundPath = fs.getPath(notFoundURI.toString());

            // WHEN
            k3po.start();
            String notFoundBody = Files.readString(notFoundPath);
            k3po.finish();

            // THEN
            assertThat(notFoundBody, equalTo(""));
        }
    }

    @Test
    @Specification({
        "${app}/read.notfound.success/server",
    })
    public void shouldReadStringNotFoundSuccess() throws Exception
    {
        // GIVEN
        URI helloURI = URI.create("http://localhost:8080/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(helloURI, Map.of()))
        {
            Path helloPath = fs.getPath(helloURI.toString());

            // WHEN
            k3po.start();
            String helloBody1 = Files.readString(helloPath);
            String helloBody2 = Files.readString(helloPath);
            k3po.finish();

            // THEN
            assertThat(helloBody1, equalTo(""));
            assertThat(helloBody2, equalTo("Hello World!"));
        }
    }

    @Test
    @Specification({
        "${app}/read.success/server",
    })
    public void shouldReadInputStream() throws Exception
    {
        // GIVEN
        URI helloURI = URI.create("http://localhost:8080/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(helloURI, Map.of()))
        {
            Path helloPath = fs.getPath(helloURI.toString());

            // WHEN
            k3po.start();
            InputStream helloIs = Files.newInputStream(helloPath);
            String helloBody = new String(helloIs.readAllBytes());
            helloIs.close();
            k3po.finish();

            // THEN
            assertThat(helloBody, equalTo("Hello World!"));
        }
    }

    @Test
    @Specification({
        "${app}/read.success.authorization/server",
    })
    public void shouldReadInputStreamWithAuthorization() throws Exception
    {
        // GIVEN
        URI helloURI = URI.create("http://localhost:8080/hello.txt");
        try (FileSystem fs = FileSystems.newFileSystem(helloURI, Map.of(AUTHORIZATION_PROPERTY_NAME, "Basic YWRtaW46dGVzdA==")))
        {
            Path helloPath = fs.getPath(helloURI.toString());

            // WHEN
            k3po.start();
            InputStream helloIs = Files.newInputStream(helloPath);
            String helloBody = new String(helloIs.readAllBytes());
            helloIs.close();
            k3po.finish();

            // THEN
            assertThat(helloBody, equalTo("Hello World!"));
        }
    }

    @Test
    @Specification({
        "${app}/read.notfound/server",
    })
    public void shouldReadInputStreamNotFound() throws Exception
    {
        // GIVEN
        URI notFoundURI = URI.create("http://localhost:8080/notfound.txt");
        try (FileSystem fs = FileSystems.newFileSystem(notFoundURI, Map.of()))
        {
            Path notFoundPath = fs.getPath(notFoundURI.toString());

            // WHEN
            k3po.start();
            InputStream notFoundIs = Files.newInputStream(notFoundPath);
            String notFoundBody = new String(notFoundIs.readAllBytes());
            notFoundIs.close();
            k3po.finish();

            // THEN
            assertThat(notFoundBody, equalTo(""));
        }
    }

    @Test
    @Specification({
        "${app}/watch/server",
    })
    public void shouldWatch() throws Exception
    {
        // GIVEN
        URI uri = URI.create("http://localhost:8080/hello.txt");
        Map<String, String> env = Map.of(POLL_INTERVAL_PROPERTY_NAME, "PT0S");
        try (FileSystem fs = FileSystems.newFileSystem(uri, env))
        {
            Path path = fs.getPath(uri.toString());

            try (WatchService watcher = fs.newWatchService())
            {
                // WHEN
                k3po.start();

                k3po.notifyBarrier("REGISTERED");
                path.register(watcher);

                WatchKey key1 = watcher.take();
                List<WatchEvent<?>> events1 = key1.pollEvents();

                k3po.notifyBarrier("MODIFIED");

                WatchKey key2 = watcher.take();
                List<WatchEvent<?>> events2 = key2.pollEvents();

                k3po.finish();

                // THEN
                assertThat(events1.size(), equalTo(1));
                assertThat(events1.get(0).kind(), equalTo(ENTRY_CREATE));
                assertThat(events1.get(0).context(), equalTo(path));
                assertThat(events2.size(), equalTo(1));
                assertThat(events2.get(0).kind(), equalTo(ENTRY_MODIFY));
                assertThat(events2.get(0).context(), equalTo(path));
            }
        }
    }

    @Test
    @Specification({
        "${app}/watch.read/server",
    })
    public void shouldWatchRead() throws Exception
    {
        // GIVEN
        URI uri = URI.create("http://localhost:8080/hello.txt");
        Map<String, String> env = Map.of(POLL_INTERVAL_PROPERTY_NAME, "PT0S");
        try (FileSystem fs = FileSystems.newFileSystem(uri, env))
        {
            Path path = fs.getPath(uri.toString());

            try (WatchService watcher = fs.newWatchService())
            {
                // WHEN
                k3po.start();

                String body1 = Files.readString(path);

                path.register(watcher);

                watcher.take();

                String body2 = Files.readString(path);

                k3po.finish();

                // THEN
                assertThat(body1, equalTo("Hello World!"));
                assertThat(body2, equalTo("Hello Universe!"));
            }
        }
    }
}
