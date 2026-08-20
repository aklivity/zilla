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
package io.aklivity.zilla.runtime.engine.test.internal.router;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.router.RouteableContext;
import io.aklivity.zilla.runtime.engine.router.Router;

public final class TestRouter implements Router
{
    public static final String NAME = "test";

    private final List<String> labels;
    private final Map<String, Integer> labelIds;
    private final List<BiConsumer<String, Integer>> listeners;

    public TestRouter(
        Configuration config)
    {
        this.labels = new CopyOnWriteArrayList<>();
        this.labelIds = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public TestRouterContext supply(
        RouteableContext context)
    {
        return new TestRouterContext(this, context);
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
}
