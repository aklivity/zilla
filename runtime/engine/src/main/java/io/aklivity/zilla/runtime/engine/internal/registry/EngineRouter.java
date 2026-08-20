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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.nio.channels.Channels.newReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.router.RouteableContext;
import io.aklivity.zilla.runtime.engine.router.Router;
import io.aklivity.zilla.runtime.engine.router.RouterContext;

public final class EngineRouter implements Router
{
    public static final String NAME = "engine";

    private final List<String> labels;
    private final Map<String, Integer> labelIds;
    private final List<BiConsumer<String, Integer>> listeners;

    public EngineRouter(
        Path directory)
    {
        this.labels = new CopyOnWriteArrayList<>();
        this.labelIds = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();

        seedLabels(directory.resolve("labels"));
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public RouterContext supply(
        RouteableContext context)
    {
        return new EngineRouterContext(this, context.streamFactory());
    }

    @Override
    public int supplyLabelId(
        String label)
    {
        Integer labelId = labelIds.get(label);

        if (labelId == null)
        {
            synchronized (labels)
            {
                labelId = labelIds.computeIfAbsent(label, this::registerLabel);
            }
        }

        return labelId;
    }

    @Override
    public String supplyLabel(
        int labelId)
    {
        return labels.get(labelId - 1);
    }

    @Override
    public void watchLabels(
        BiConsumer<String, Integer> listener)
    {
        listeners.add(listener);
    }

    private int registerLabel(
        String label)
    {
        labels.add(label);
        int labelId = labels.size();

        listeners.forEach(listener -> listener.accept(label, labelId));

        return labelId;
    }

    private void seedLabels(
        Path labelsPath)
    {
        try
        {
            Files.createDirectories(labelsPath.getParent());

            try (FileChannel channel = FileChannel.open(labelsPath, CREATE, READ, WRITE))
            {
                try (BufferedReader in = new BufferedReader(newReader(channel, UTF_8.name())))
                {
                    for (String label = in.readLine(); label != null; label = in.readLine())
                    {
                        labels.add(label);
                        labelIds.put(label, labels.size());
                    }
                }
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
