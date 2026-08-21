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
import io.aklivity.zilla.runtime.model.core.ext.StringController;
import io.aklivity.zilla.runtime.model.core.ext.StringEvent;
import io.aklivity.zilla.runtime.model.core.ext.StringSink;
import io.aklivity.zilla.runtime.model.core.ext.StringSource;
import io.aklivity.zilla.runtime.model.core.ext.StringTransform;

// The string model's own stage vocabulary bound to CoreExtModelPipeline's pump. The chain is folded once
// here, right to left, so each stage holds its downstream directly and an event walks the whole
// composition in one pass; the controller, source, and terminal sink are per-pipeline views over the
// pump's state, allocated once and reused for every event of every value this stream carries.
final class StringExtModelPipeline extends CoreExtModelPipeline
{
    private final StringController control;
    private final StringSource source;
    private final StringSink head;

    StringExtModelPipeline(
        CoreModelHandler handler,
        CoreModelValidator validator,
        boolean lenient,
        List<StringTransform> transforms,
        ModelEnvelope envelope,
        int padding)
    {
        super(handler, validator, lenient, envelope, padding);
        this.control = new Control();
        this.source = new Source();

        StringSink sink = new Terminal();
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

    private static StringEvent event(
        ValueEvent event)
    {
        return switch (event)
        {
        case START_VALUE -> StringEvent.START_VALUE;
        case SEGMENT -> StringEvent.SEGMENT;
        case END_VALUE -> StringEvent.END_VALUE;
        };
    }

    private final class Control implements StringController
    {
        @Override
        public long authorization()
        {
            return StringExtModelPipeline.this.authorization();
        }

        @Override
        public ModelEnvelope envelope()
        {
            return StringExtModelPipeline.this.envelope();
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            StringExtModelPipeline.this.consumed(sourceBytes);
        }

        @Override
        public void reject(
            String diagnostic)
        {
            StringExtModelPipeline.this.reject(diagnostic);
        }

        @Override
        public void withhold()
        {
            StringExtModelPipeline.this.withhold();
        }
    }

    private final class Source implements StringSource
    {
        @Override
        public DirectBufferEx getSegment()
        {
            return segment();
        }
    }

    // one stage bound to its downstream, so a stage never learns whether that downstream is another stage
    // or the terminal
    private final class Stage implements StringSink
    {
        private final StringTransform transform;
        private final StringSink sink;

        private Stage(
            StringTransform transform,
            StringSink sink)
        {
            this.transform = transform;
            this.sink = sink;
        }

        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event)
        {
            return transform.transform(control, source, event, sink);
        }

        @Override
        public ModelStatus resume(
            StringController control,
            StringSource source,
            StringEvent event)
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
    private final class Terminal implements StringSink
    {
        @Override
        public ModelStatus transform(
            StringController control,
            StringSource source,
            StringEvent event)
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
            StringController control,
            StringSource source,
            StringEvent event)
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
