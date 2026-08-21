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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.model.core.ext.BytesController;
import io.aklivity.zilla.runtime.model.core.ext.BytesEvent;
import io.aklivity.zilla.runtime.model.core.ext.BytesSink;
import io.aklivity.zilla.runtime.model.core.ext.BytesSource;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;

// The bytes model's own stage vocabulary bound to CoreExtModelPipeline's pump. The chain is folded once
// here, right to left, so each stage holds its downstream directly and an event walks the whole
// composition in one pass; the controller, source, and terminal sink are per-pipeline views over the
// pump's state, allocated once and reused for every event of every value this stream carries.
final class BytesExtModelPipeline extends CoreExtModelPipeline
{
    private final BytesController control;
    private final BytesSource source;
    private final BytesSink head;

    BytesExtModelPipeline(
        CoreModelHandler handler,
        CoreModelValidator validator,
        boolean lenient,
        List<BytesTransform> transforms,
        ModelEnvelope envelope,
        int padding)
    {
        super(handler, validator, lenient, envelope, padding);
        this.control = new Control();
        this.source = new Source();

        BytesSink sink = new Terminal();
        for (int i = transforms.size() - 1; i >= 0; i--)
        {
            sink = new Stage(transforms.get(i), sink);
        }
        this.head = sink;
    }

    @Override
    public boolean identity()
    {
        return head.identity();
    }

    @Override
    protected ModelStatus feed(
        ValueEvent event)
    {
        return head.transform(control, source, event(event));
    }

    @Override
    protected ModelStatus resume(
        ValueEvent event)
    {
        return head.resume(control, source, event(event));
    }

    @Override
    protected void resetChain()
    {
        head.reset();
    }

    private static BytesEvent event(
        ValueEvent event)
    {
        return switch (event)
        {
        case START_VALUE -> BytesEvent.START_VALUE;
        case SEGMENT -> BytesEvent.SEGMENT;
        case END_VALUE -> BytesEvent.END_VALUE;
        };
    }

    private final class Control implements BytesController
    {
        @Override
        public long authorization()
        {
            return BytesExtModelPipeline.this.authorization();
        }

        @Override
        public ModelEnvelope envelope()
        {
            return BytesExtModelPipeline.this.envelope();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            BytesExtModelPipeline.this.consumed(sourceBytes);
        }

        @Override
        public void reject(
            String diagnostic)
        {
            BytesExtModelPipeline.this.reject(diagnostic);
        }

        @Override
        public void withhold()
        {
            BytesExtModelPipeline.this.withhold();
        }
    }

    private final class Source implements BytesSource
    {
        @Override
        public DirectBufferEx getSegment()
        {
            return segment();
        }
    }

    // one stage bound to its downstream, so a stage never learns whether that downstream is another stage
    // or the terminal
    private final class Stage implements BytesSink
    {
        private final BytesTransform transform;
        private final BytesSink sink;

        private Stage(
            BytesTransform transform,
            BytesSink sink)
        {
            this.transform = transform;
            this.sink = sink;
        }

        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event)
        {
            return transform.transform(control, source, event, sink);
        }

        @Override
        public ModelStatus resume(
            BytesController control,
            BytesSource source,
            BytesEvent event)
        {
            return transform.resume(control, source, event, sink);
        }

        @Override
        public void reset()
        {
            transform.reset();
            sink.reset();
        }

        @Override
        public boolean identity()
        {
            return transform.identity() && sink.identity();
        }
    }

    // the terminal: value bytes land in the caller's destination, and a write the destination bounded
    // reports what it took so the segment resumes from its remainder
    private final class Terminal implements BytesSink
    {
        @Override
        public ModelStatus transform(
            BytesController control,
            BytesSource source,
            BytesEvent event)
        {
            ModelStatus status = ModelStatus.OK;
            if (event.segmented())
            {
                DirectBufferEx segment = source.getSegment();
                int length = segment.capacity();
                int written = write(segment, 0, length);
                if (written < length)
                {
                    control.consumed(written);
                    status = ModelStatus.OVERFLOW;
                }
            }
            return status;
        }

        @Override
        public ModelStatus resume(
            BytesController control,
            BytesSource source,
            BytesEvent event)
        {
            return transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
