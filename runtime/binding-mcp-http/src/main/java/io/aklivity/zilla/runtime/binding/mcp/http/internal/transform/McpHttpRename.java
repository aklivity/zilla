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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.json.stream.JsonLocation;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Composable forwarding {@link JsonTransform} that rewrites top-level object keys per a rename map,
 * leaving every value (including whole object/array subtrees) untouched. Chained after a projector,
 * it realizes the rename portion of a request body template without materializing any value.
 */
public final class McpHttpRename implements JsonTransform
{
    private final Map<String, String> renames;
    private final KeySource keySource = new KeySource();

    private int depth;

    public McpHttpRename(
        Map<String, String> renames)
    {
        this.renames = renames;
    }

    @Override
    public void reset()
    {
        depth = 0;
    }

    @Override
    public Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            status = sink.feed(control, source, event);
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            status = sink.feed(control, source, event);
            break;
        case KEY_NAME:
            final String renamed = depth == 1 ? renames.get(source.getStringView().toString()) : null;
            status = renamed != null
                ? sink.feed(control, keySource.with(renamed), event)
                : sink.feed(control, source, event);
            break;
        default:
            status = sink.feed(control, source, event);
            break;
        }
        return status;
    }

    private static final class KeySource implements JsonSource
    {
        private CharSequence key;

        private KeySource with(
            CharSequence key)
        {
            this.key = key;
            return this;
        }

        @Override
        public String getString()
        {
            return key == null ? null : key.toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return key;
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBuffer getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBuffer getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }
    }
}
