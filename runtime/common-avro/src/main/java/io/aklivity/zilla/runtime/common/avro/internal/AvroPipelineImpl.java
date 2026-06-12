/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.avro.internal;

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.ADVANCED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.SUSPENDED;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

/**
 * Backs {@link AvroPipeline}: a thin pump that re-targets the {@link AvroParser} cursor at the frame
 * buffer and pushes each pulled event through the stage chain to the terminal sink. It owns the
 * per-edge handles the stages see — a read-only {@link AvroSource} view that delegates to the parser's
 * accessors (without exposing the cursor, so a stage cannot disturb the pump) and an
 * {@link AvroController} that forwards a stage's segment request — leaving the parser a pure cursor.
 * <p>
 * The status is whatever the sink reports — completion is the sink's to signal; malformed binary aborts
 * with {@code REJECTED}. On {@code SUSPENDED} (bounded output full) the parser keeps its position, so a
 * resume {@code feed} continues from where it paused — its buffer arguments are ignored.
 */
final class AvroPipelineImpl implements AvroPipeline
{
    private final AvroParser parser;
    private final Source source;
    private final Control control;
    private final AvroSink root;

    private boolean suspended;

    AvroPipelineImpl(
        AvroParser parser,
        AvroSink root)
    {
        this.parser = parser;
        this.source = new Source(parser);
        this.control = new Control();
        this.root = root;
    }

    @Override
    public void reset()
    {
        parser.reset();
        root.reset();
        suspended = false;
    }

    @Override
    public long position()
    {
        return parser.getLocation().position();
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        Status status = ADVANCED;
        try
        {
            if (suspended)
            {
                status = root.resume(control, source);
            }
            else
            {
                parser.wrap(buffer, offset, length, last);
            }
            while (status == ADVANCED)
            {
                AvroEvent event = parser.nextEvent(control.mode());
                if (event == null)
                {
                    break;
                }
                status = root.feed(control, source, event);
            }
        }
        catch (AvroValidationException ex)
        {
            status = REJECTED;
        }
        suspended = status == SUSPENDED;
        return status;
    }

    // the read-only view handed to stages: the parser's accessors without its cursor, so a stage reads the
    // current value but cannot advance the pump
    private static final class Source implements AvroSource
    {
        private final AvroParser parser;

        private Source(
            AvroParser parser)
        {
            this.parser = parser;
        }

        @Override
        public boolean getBoolean()
        {
            return parser.getBoolean();
        }

        @Override
        public int getInt()
        {
            return parser.getInt();
        }

        @Override
        public long getLong()
        {
            return parser.getLong();
        }

        @Override
        public float getFloat()
        {
            return parser.getFloat();
        }

        @Override
        public double getDouble()
        {
            return parser.getDouble();
        }

        @Override
        public String getString()
        {
            return parser.getString();
        }

        @Override
        public String getField()
        {
            return parser.getField();
        }

        @Override
        public String getKey()
        {
            return parser.getKey();
        }

        @Override
        public DirectBuffer getSegment()
        {
            return parser.getSegment();
        }

        @Override
        public int deferredBytes()
        {
            return parser.deferredBytes();
        }

        @Override
        public AvroType type()
        {
            return parser.type();
        }

        @Override
        public AvroLocation getLocation()
        {
            return parser.getLocation();
        }
    }

    // the head edge's controller: a stage's segmentable() request becomes a one-shot SEGMENTED mode that the
    // pump passes to the parser on the next pull
    private static final class Control implements AvroController
    {
        private boolean segmented;

        @Override
        public void segmentable()
        {
            segmented = true;
        }

        private AvroParser.Mode mode()
        {
            AvroParser.Mode mode = segmented ? AvroParser.Mode.SEGMENTED : AvroParser.Mode.STRUCTURED;
            segmented = false;
            return mode;
        }
    }
}
