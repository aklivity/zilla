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
import java.util.concurrent.atomic.AtomicInteger;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.router.RouteableContext;
import io.aklivity.zilla.runtime.engine.router.Router;
import io.aklivity.zilla.runtime.engine.util.function.ObjectIntBiConsumer;

public final class TestRouter implements Router
{
    public static final String NAME = "test";

    private final Map<String, Integer> labelIds;
    private final Map<Integer, String> labels;
    private final AtomicInteger nextLabelId;
    private final List<ObjectIntBiConsumer<String>> listeners;

    public TestRouter(
        Configuration config)
    {
        this.labelIds = new ConcurrentHashMap<>();
        this.labels = new ConcurrentHashMap<>();
        this.nextLabelId = new AtomicInteger();
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
        return labelIds.computeIfAbsent(label, this::registerLabel);
    }

    @Override
    public String supplyLabel(
        int labelId)
    {
        return labels.get(labelId);
    }

    @Override
    public void watchLabels(
        ObjectIntBiConsumer<String> listener)
    {
        listeners.add(listener);
    }

    private int registerLabel(
        String label)
    {
        int labelId = nextLabelId.incrementAndGet();
        labels.put(labelId, label);

        listeners.forEach(listener -> listener.accept(label, labelId));

        return labelId;
    }
}
