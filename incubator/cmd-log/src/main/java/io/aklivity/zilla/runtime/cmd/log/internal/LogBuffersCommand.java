/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cmd.log.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.cmd.log.internal.layouts.BufferPoolLayout;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public final class LogBuffersCommand implements Runnable
{
    private static final Pattern BUFFERS_PATTERN = Pattern.compile("buffers\\d+");

    private final Path directory;
    private final boolean verbose;
    private final Logger out;

    private final Map<Path, BufferPoolLayout> layoutsByPath;

    public LogBuffersCommand(
        EngineConfiguration config,
        Logger out,
        boolean verbose)
    {
        this.directory = config.directory();
        this.out = out;
        this.verbose = verbose;
        this.layoutsByPath = new LinkedHashMap<>();
    }

    private boolean isBuffersFile(
        Path path)
    {
        return path.getNameCount() - directory.getNameCount() == 1 &&
               BUFFERS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
               Files.isRegularFile(path);
    }

    private void onDiscovered(
        Path path)
    {
        if (verbose)
        {
            out.printf("Discovered: %s\n", path);
        }
    }

    private void displaySlotOffsets(
        Path path)
    {
        BufferPoolLayout layout = layoutsByPath.computeIfAbsent(path, this::newBufferPoolLayout);
        final String name = path.getFileName().toString();
        displaySlotOffsets(name, layout.bufferPool());
    }

    private BufferPoolLayout newBufferPoolLayout(
        Path path)
    {
        return new BufferPoolLayout.Builder()
                .path(path)
                .readonly(true)
                .build();
    }

    private void displaySlotOffsets(
        String name,
        DefaultBufferPool bufferPool)
    {
        final DirectBuffer poolBuffer = bufferPool.poolBuffer();
        final int slotCapacity = bufferPool.slotCapacity();
        final int slotCount = bufferPool.slotCount();
        final int slotOffsetsAt = slotCapacity * slotCount;

        for (int slot = 0; slot < slotCount; slot++)
        {
            final long streamId = poolBuffer.getLong(slotOffsetsAt + slot * Long.BYTES);
            if (streamId != 0L)
            {
                final long slotOffset = slot * slotCapacity;
                out.printf("%s [0x%016x] [0x%08x]\n", name, streamId, slotOffset);
            }
        }
    }

    @Override
    public void run()
    {
        try (Stream<Path> files = Files.walk(directory, 3))
        {
            files.filter(this::isBuffersFile)
                 .peek(this::onDiscovered)
                 .forEach(this::displaySlotOffsets);
            out.printf("\n");
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
