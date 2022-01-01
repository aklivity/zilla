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

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.cmd.log.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.cmd.log.internal.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class LogQueueDepthCommand implements Runnable
{
    private static final Pattern STREAMS_PATTERN = Pattern.compile("data\\d+");

    private final Path directory;
    private final boolean verbose;
    private final boolean separator;
    private final Logger out;

    private final Map<Path, StreamsLayout> layoutsByPath;

    public LogQueueDepthCommand(
        EngineConfiguration config,
        Logger out,
        boolean verbose,
        boolean separator)
    {
        this.directory = config.directory();
        this.out = out;
        this.verbose = verbose;
        this.separator = separator;
        this.layoutsByPath = new LinkedHashMap<>();
    }

    private boolean isStreamsFile(
        Path path)
    {
        return path.getNameCount() - directory.getNameCount() == 1 &&
               STREAMS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
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

    private void displayQueueDepth(
        Path path)
    {
        StreamsLayout layout = layoutsByPath.computeIfAbsent(path, this::newStreamsLayout);
        final String name = path.getFileName().toString();
        displayQueueDepth(name, layout.streamsBuffer());
    }

    private StreamsLayout newStreamsLayout(
        Path path)
    {
        return new StreamsLayout.Builder()
                .path(path)
                .readonly(true)
                .build();
    }

    private void displayQueueDepth(
        String name,
        RingBufferSpy buffer)
    {
        // read consumer position first for pessimistic queue depth
        long consumerAt = buffer.consumerPosition();
        long producerAt = buffer.producerPosition();

        final String valueFormat = separator ? ",d" : "d";

        out.printf("{\"name\":\"%s\", " +
                    "\"head\":%" + valueFormat + ", " +
                    "\"tail\":%" + valueFormat + ", " +
                    "\"depth\":%" + valueFormat + "}\n",
                    name, consumerAt, producerAt, producerAt - consumerAt);
    }

    @Override
    public void run()
    {
        try (Stream<Path> files = Files.walk(directory, 3))
        {
            files.filter(this::isStreamsFile)
                 .peek(this::onDiscovered)
                 .forEach(this::displayQueueDepth);
            out.printf("\n");
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
