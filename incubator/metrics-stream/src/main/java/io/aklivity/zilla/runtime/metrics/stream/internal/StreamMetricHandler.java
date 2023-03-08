/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.stream.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;

public class StreamMetricHandler implements MetricHandler
{
    private final String name;
    private final Event event;
    private final long bindingId;

    public StreamMetricHandler(
        String name,
        Event event,
        long bindingId)
    {
        this.name = name;
        this.event = event;
        this.bindingId = bindingId;
    }

    @Override
    public void onEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        System.out.format("%s %s %d %d %d %d\n", name, event, bindingId, msgTypeId, index, length);
    }
}
