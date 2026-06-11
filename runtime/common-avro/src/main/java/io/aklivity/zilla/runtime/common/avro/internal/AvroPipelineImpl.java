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

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.PENDING;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

/**
 * Backs {@link AvroPipeline}: pulls events from the bound {@link AvroParserImpl} and pushes each
 * through the root {@link AvroSink}, passing the parser itself as both the immutable source view and
 * the control handle. The status is whatever the sink reports; if the sink never completes but the
 * parser reaches the end of the message, the datum is {@code COMPLETE}; malformed binary aborts with
 * {@code REJECTED}.
 */
final class AvroPipelineImpl implements AvroPipeline
{
    private final AvroParserImpl parser;
    private final AvroSink root;

    AvroPipelineImpl(
        AvroParserImpl parser,
        AvroSink root)
    {
        this.parser = parser;
        this.root = root;
    }

    @Override
    public void reset()
    {
        parser.reset();
        root.reset();
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        parser.wrap(buffer, offset, length);
        Status status = PENDING;
        try
        {
            while (status == PENDING && parser.hasNext())
            {
                status = root.feed(parser, parser, parser.nextEvent());
            }
        }
        catch (AvroValidationException ex)
        {
            status = REJECTED;
        }
        if (status == PENDING && parser.complete())
        {
            status = COMPLETE;
        }
        return status;
    }
}
