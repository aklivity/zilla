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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

// One stage per extractKey / extractHeaders config entry. It watches the lane its configured path is
// rooted in and, on a match, switches to the target lane, appends the matched content there, and switches
// back — all within the call that surfaced the match, so nothing is captured for a later replay. The
// matched event then flows on to its own destination untouched: extraction only ever adds to the key or
// headers, never mutates the value.
final class KafkaExtractTransform implements KafkaTransform
{
    private final KafkaEvent origin;
    private final KafkaEvent target;
    private final String path;
    private final Extracted extracted;

    private KafkaEvent lane;

    KafkaExtractTransform(
        KafkaEvent origin,
        KafkaEvent target,
        String path,
        String name)
    {
        this.origin = origin;
        this.target = target;
        this.path = path;
        this.extracted = new Extracted(name);
    }

    @Override
    public ModelStatus transform(
        KafkaController control,
        KafkaSource source,
        KafkaEvent event,
        KafkaSink sink)
    {
        ModelStatus status = ModelStatus.OK;

        if (event != KafkaEvent.FIELD)
        {
            // a lane switch raised by any stage, including one upstream of this one
            lane = event;
        }
        else if (lane == origin && path.equals(source.getPath()))
        {
            status = append(control, source, sink);
        }

        return status == ModelStatus.REJECTED
            ? status
            : sink.transform(control, source, event);
    }

    @Override
    public void reset()
    {
        lane = null;
    }

    private ModelStatus append(
        KafkaController control,
        KafkaSource source,
        KafkaSink sink)
    {
        extracted.value = source.getValue();

        ModelStatus status = sink.transform(control, source, target);

        if (status != ModelStatus.REJECTED)
        {
            status = sink.transform(control, extracted, KafkaEvent.FIELD);
        }

        if (status != ModelStatus.REJECTED)
        {
            status = sink.transform(control, source, origin);
        }

        return status;
    }

    // the view handed to the target lane: the matched bytes under the name that lane knows them by, a
    // header name for the headers lane and the source path for the key lane, which has no name of its own
    private static final class Extracted implements KafkaSource
    {
        private final String name;

        private DirectBufferEx value;

        private Extracted(
            String name)
        {
            this.name = name;
        }

        @Override
        public String getPath()
        {
            return name;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return value;
        }
    }
}
