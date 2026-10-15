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
package io.aklivity.zilla.runtime.binding.filesystem.internal;

import static io.aklivity.zilla.runtime.binding.filesystem.internal.stream.FileSystemServerFactory.FILE_CHANGED_SIGNAL_ID;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class FileSystemWatcherTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldHashWholeWatchedFile() throws Exception
    {
        Path path = folder.newFile("large.txt").toPath();
        Files.writeString(path, "a".repeat(16383) + "b");

        FileSystemWatcher watcher = new FileSystemWatcher(mock(Signaler.class));
        FileSystemWatcher.WatchedFile watched = watchedFile(path, () -> open(path));

        assertThat(watcher.calculateHash(watched), equalTo("830cc3416ed05824855e80b49e242446"));
    }

    @Test
    public void shouldNotHashUnreadableWatchedFile() throws Exception
    {
        Path path = folder.getRoot().toPath().resolve("missing.txt");

        FileSystemWatcher watcher = new FileSystemWatcher(mock(Signaler.class));
        FileSystemWatcher.WatchedFile watched = watchedFile(path, () -> null);

        assertThat(watcher.calculateHash(watched), nullValue());
    }

    @Test
    public void shouldSignalWatchedFileAfterSiblingUnregistered() throws Exception
    {
        Path config = folder.newFile("zilla.yaml").toPath();
        Path other = folder.newFile("other.txt").toPath();
        Files.writeString(config, "v1");
        Files.writeString(other, "secret");

        Signaler signaler = mock(Signaler.class);
        FileSystemWatcher watcher = new FileSystemWatcher(signaler);
        LinkOption[] nofollow = new LinkOption[] { NOFOLLOW_LINKS };
        FileSystemWatcher.WatchedFile watchedConfig = watchedFile(config, nofollow, 1L);
        FileSystemWatcher.WatchedFile watchedOther = watchedFile(other, nofollow, 2L);

        watcher.watch(watchedConfig);
        watcher.watch(watchedOther);
        watcher.unregister(watchedOther);

        ExecutorService executor = newSingleThreadExecutor();
        try
        {
            executor.submit(watcher);
            Files.writeString(config, "v2");

            verify(signaler, timeout(30000L)).signalNow(0L, 0L, 1L, 0L, FILE_CHANGED_SIGNAL_ID, 0);
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldSignalSymlinkedFileAfterSiblingUnregisteredAndDataSwapped() throws Exception
    {
        Path directory = folder.getRoot().toPath().toRealPath();
        Path version1 = Files.createDirectory(directory.resolve("..v1"));
        Files.writeString(version1.resolve("zilla.yaml"), "v1");
        Files.writeString(version1.resolve("other.txt"), "secret");
        Files.createSymbolicLink(directory.resolve("..data"), version1.getFileName());
        Path config = Files.createSymbolicLink(directory.resolve("zilla.yaml"), Path.of("..data", "zilla.yaml"));
        Path other = Files.createSymbolicLink(directory.resolve("other.txt"), Path.of("..data", "other.txt"));

        Signaler signaler = mock(Signaler.class);
        FileSystemWatcher watcher = new FileSystemWatcher(signaler);
        LinkOption[] follow = new LinkOption[0];
        FileSystemWatcher.WatchedFile watchedConfig = watchedFile(config, follow, 1L);
        FileSystemWatcher.WatchedFile watchedOther = watchedFile(other, follow, 2L);

        watcher.watch(watchedConfig);
        watcher.watch(watchedOther);
        watcher.unregister(watchedOther);

        ExecutorService executor = newSingleThreadExecutor();
        try
        {
            executor.submit(watcher);

            Path version2 = Files.createDirectory(directory.resolve("..v2"));
            Files.writeString(version2.resolve("zilla.yaml"), "v2");
            Files.writeString(version2.resolve("other.txt"), "secret");
            Path swap = Files.createSymbolicLink(directory.resolve("..data_tmp"), version2.getFileName());
            Files.move(swap, directory.resolve("..data"), ATOMIC_MOVE, REPLACE_EXISTING);
            Files.delete(version1.resolve("zilla.yaml"));
            Files.delete(version1.resolve("other.txt"));
            Files.delete(version1);

            verify(signaler, timeout(30000L)).signalNow(0L, 0L, 1L, 0L, FILE_CHANGED_SIGNAL_ID, 0);
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private static FileSystemWatcher.WatchedFile watchedFile(
        Path path,
        Supplier<InputStream> input)
    {
        return new FileSystemWatcher.WatchedFile(path, new LinkOption[0], input, "", 0L, 0L, 0L, 0L);
    }

    private static FileSystemWatcher.WatchedFile watchedFile(
        Path path,
        LinkOption[] symlinks,
        long replyId) throws IOException
    {
        String hash;
        try (InputStream input = open(path))
        {
            hash = new FileSystemDigest(new byte[8192]).digest(input);
        }
        return new FileSystemWatcher.WatchedFile(path, symlinks, () -> open(path), hash, 0L, 0L, 0L, replyId);
    }

    private static InputStream open(
        Path path)
    {
        try
        {
            return Files.newInputStream(path);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }
}
