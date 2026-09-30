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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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

    private static FileSystemWatcher.WatchedFile watchedFile(
        Path path,
        Supplier<InputStream> input)
    {
        return new FileSystemWatcher.WatchedFile(path, new LinkOption[0], input, "", 0L, 0L, 0L, 0L);
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
