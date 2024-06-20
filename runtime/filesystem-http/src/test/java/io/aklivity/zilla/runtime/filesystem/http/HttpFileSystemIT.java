/*
 * Copyright 2021-2023 Aklivity Inc
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

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.rules.RuleChain.outerRule;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class HttpFileSystemIT
{
    private static final String HELLO_BODY = "Hello World!";
    private static final String EMPTY_BODY = "";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/filesystem/http/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(timeout).around(k3po);

    @Test
    @Specification({
        "${app}/success/server",
    })
    public void shouldReadString() throws Exception
    {
        // GIVEN
        String helloUrl = "http://localhost:8080/hello.txt";
        Path helloPath = Path.of(new URI(helloUrl));

        // WHEN
        k3po.start();
        String helloBody = Files.readString(helloPath);
        k3po.finish();

        // THEN
        assertThat(helloBody, equalTo(HELLO_BODY));
    }

    @Test
    @Specification({
        "${app}/notfound/server",
    })
    public void shouldReadStringNotFound() throws Exception
    {
        // GIVEN
        String notFoundUrl = "http://localhost:8080/notfound.txt";
        Path notFoundPath = Path.of(new URI(notFoundUrl));

        // WHEN
        k3po.start();
        String notFoundBody = Files.readString(notFoundPath);
        k3po.finish();

        // THEN
        assertThat(notFoundBody, equalTo(EMPTY_BODY));
    }

    @Test
    @Specification({
        "${app}/success/server",
    })
    public void shouldReadInputStream() throws Exception
    {
        // GIVEN
        String helloUrl = "http://localhost:8080/hello.txt";
        Path helloPath = Path.of(new URI(helloUrl));
        FileSystemProvider fsp = helloPath.getFileSystem().provider();

        // WHEN
        k3po.start();
        InputStream helloIs = fsp.newInputStream(helloPath);
        String helloBody = new String(helloIs.readAllBytes());
        helloIs.close();
        k3po.finish();

        // THEN
        assertThat(helloBody, equalTo(HELLO_BODY));
    }

    @Test
    @Specification({
        "${app}/notfound/server",
    })
    public void shouldReadInputStreamNotFound() throws Exception
    {
        // GIVEN
        String notFoundUrl = "http://localhost:8080/notfound.txt";
        Path notFoundPath = Path.of(new URI(notFoundUrl));
        FileSystemProvider fsp = notFoundPath.getFileSystem().provider();

        // WHEN
        k3po.start();
        InputStream notFoundIs = fsp.newInputStream(notFoundPath);
        String notFoundBody = new String(notFoundIs.readAllBytes());
        notFoundIs.close();
        k3po.finish();

        // THEN
        assertThat(notFoundBody, equalTo(EMPTY_BODY));
    }

    @Test
    @Specification({
        "${app}/watch/server",
    })
    public void shouldWatch() throws Exception
    {
        // GIVEN
        String url = "http://localhost:8080/hello.txt";
        Path path = Path.of(new URI(url));
        HttpWatchService watchService = (HttpWatchService) path.getFileSystem().newWatchService();
        watchService.pollSeconds(1);
        path.register(watchService);

        // WHEN
        k3po.start();
        WatchKey key1 = watchService.take();
        List<WatchEvent<?>> events1 = key1.pollEvents();
        k3po.notifyBarrier("CHANGED");
        WatchKey key2 = watchService.take();
        List<WatchEvent<?>> events2 = key2.pollEvents();
        watchService.close();
        k3po.finish();

        // THEN
        assertThat(events1.size(), equalTo(1));
        assertThat(events1.get(0).kind(), equalTo(ENTRY_MODIFY));
        assertThat(events1.get(0).context(), equalTo(path));
        assertThat(events2.size(), equalTo(1));
        assertThat(events1.get(0).kind(), equalTo(ENTRY_MODIFY));
        assertThat(events2.get(0).context(), equalTo(path));
    }

    @Ignore // TODO: Ati
    @Test
    @Specification({
        "${app}/watch/server",
    })
    public void shouldWatchReadString() throws Exception
    {
        // GIVEN
        String url = "http://localhost:8080/hello.txt";
        Path path = Path.of(new URI(url));
        HttpWatchService watchService = (HttpWatchService) path.getFileSystem().newWatchService();
        watchService.pollSeconds(1);
        path.register(watchService);

        // WHEN
        k3po.start();
        watchService.take();
        String body1 = Files.readString(path);
        k3po.notifyBarrier("CHANGED");
        watchService.take();
        String body2 = Files.readString(path);
        watchService.close();
        k3po.finish();

        // THEN
        assertThat(body1, equalTo("Hello World!"));
        assertThat(body2, equalTo("Hello Universe!"));
    }

    @Ignore // TODO: Ati
    @Test
    @Specification({
        "${app}/watch.error.success/server",
    })
    public void shouldWatchErrorSuccess() throws Exception
    {
        // GIVEN
        String url = "http://localhost:8080/hello.txt";
        Path path = Path.of(new URI(url));
        HttpWatchService watchService = (HttpWatchService) path.getFileSystem().newWatchService();
        watchService.pollSeconds(1);
        path.register(watchService);

        // WHEN
        k3po.start();
        WatchKey key1 = watchService.take();
        List<WatchEvent<?>> events1 = key1.pollEvents();
        k3po.notifyBarrier("FOUND");
        WatchKey key2 = watchService.take();
        List<WatchEvent<?>> events2 = key2.pollEvents();
        watchService.close();
        k3po.finish();

        // THEN
        assertThat(events1.size(), equalTo(1));
        assertThat(events1.get(0).kind(), equalTo(ENTRY_MODIFY));
        assertThat(events1.get(0).context(), equalTo(path));
        assertThat(events2.size(), equalTo(1));
        assertThat(events1.get(0).kind(), equalTo(ENTRY_MODIFY));
        assertThat(events2.get(0).context(), equalTo(path));
    }

    @Ignore // TODO: Ati
    @Test
    @Specification({
        "${app}/watch.error.success/server",
    })
    public void shouldWatchErrorSuccessReadString() throws Exception
    {
        // GIVEN
        String url = "http://localhost:8080/hello.txt";
        Path path = Path.of(new URI(url));
        HttpWatchService watchService = (HttpWatchService) path.getFileSystem().newWatchService();
        watchService.pollSeconds(1);
        path.register(watchService);

        // WHEN
        k3po.start();
        watchService.take();
        String body1 = Files.readString(path);
        k3po.notifyBarrier("FOUND");
        watchService.take();
        String body2 = Files.readString(path);
        watchService.close();
        k3po.finish();

        // THEN
        assertThat(body1, equalTo(""));
        assertThat(body2, equalTo("Hello World!"));
    }
}
