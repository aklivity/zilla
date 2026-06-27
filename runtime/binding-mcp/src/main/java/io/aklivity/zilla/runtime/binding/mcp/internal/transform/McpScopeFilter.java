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
package io.aklivity.zilla.runtime.binding.mcp.internal.transform;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

public final class McpScopeFilter implements JsonTransform
{
    private final String arrayKey;
    private final Map<CharSequence, List<String>> scopesByName;
    private final BiPredicate<CharSequence, List<String>> admits;
    private final KeySource keySource = new KeySource();
    private final MediatingController mediatingControl = new MediatingController();

    private int depth;
    private boolean itemsArmed;
    private boolean inItems;
    private int itemDepth;
    private boolean itemPending;
    private boolean nameArmed;
    private boolean dropping;

    public McpScopeFilter(
        String arrayKey,
        Map<CharSequence, List<String>> scopesByName,
        BiPredicate<CharSequence, List<String>> admits)
    {
        this.arrayKey = arrayKey;
        this.scopesByName = scopesByName;
        this.admits = admits;
    }

    @Override
    public void reset()
    {
        depth = 0;
        itemsArmed = false;
        inItems = false;
        itemDepth = 0;
        itemPending = false;
        nameArmed = false;
        dropping = false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        mediatingControl.delegate = control;
        return inItems
            ? onItems(source, event, sink)
            : onOuter(source, event, sink);
    }

    private Status onOuter(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            if (itemsArmed && event == JsonEvent.START_ARRAY)
            {
                itemsArmed = false;
                inItems = true;
            }
            status = sink.transform(mediatingControl, source, event);
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            status = sink.transform(mediatingControl, source, event);
            break;
        case KEY_NAME:
            itemsArmed = depth == 1 && arrayKey.contentEquals(source.getStringView());
            status = sink.transform(mediatingControl, source, event);
            break;
        default:
            status = sink.transform(mediatingControl, source, event);
            break;
        }
        return status;
    }

    private Status onItems(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_OBJECT:
            itemDepth++;
            if (itemDepth == 1)
            {
                itemPending = true;
                dropping = false;
                status = Status.ADVANCED;
            }
            else if (dropping)
            {
                status = Status.ADVANCED;
            }
            else
            {
                status = sink.transform(mediatingControl, source, event);
            }
            break;
        case END_OBJECT:
            itemDepth--;
            if (dropping)
            {
                if (itemDepth == 0)
                {
                    dropping = false;
                }
                status = Status.ADVANCED;
            }
            else
            {
                status = sink.transform(mediatingControl, source, event);
            }
            break;
        case START_ARRAY:
            itemDepth++;
            status = dropping
                ? Status.ADVANCED
                : sink.transform(mediatingControl, source, event);
            break;
        case END_ARRAY:
            if (itemDepth == 0)
            {
                inItems = false;
                status = sink.transform(mediatingControl, source, event);
            }
            else
            {
                itemDepth--;
                status = dropping
                    ? Status.ADVANCED
                    : sink.transform(mediatingControl, source, event);
            }
            break;
        case KEY_NAME:
            if (dropping)
            {
                status = Status.ADVANCED;
            }
            else if (itemPending && itemDepth == 1)
            {
                nameArmed = "name".contentEquals(source.getStringView());
                status = Status.ADVANCED;
            }
            else
            {
                status = sink.transform(mediatingControl, source, event);
            }
            break;
        case VALUE_STRING:
            if (dropping)
            {
                status = Status.ADVANCED;
            }
            else if (itemPending && nameArmed)
            {
                status = resolveItem(source, event, sink);
            }
            else
            {
                status = sink.transform(mediatingControl, source, event);
            }
            break;
        default:
            status = dropping || itemPending
                ? Status.ADVANCED
                : sink.transform(mediatingControl, source, event);
            break;
        }
        return status;
    }

    private Status resolveItem(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final CharSequence name = source.getStringView();
        final List<String> scopes = scopesByName.get(name);

        itemPending = false;
        nameArmed = false;

        if (scopes != null && !admits.test(name, scopes))
        {
            dropping = true;
            return Status.ADVANCED;
        }

        mediatingControl.synthetic = true;
        Status status = sink.transform(mediatingControl, source, JsonEvent.START_OBJECT);
        if (status == Status.ADVANCED)
        {
            status = sink.transform(mediatingControl, keySource.with("name"), JsonEvent.KEY_NAME);
        }
        mediatingControl.synthetic = false;
        if (status == Status.ADVANCED)
        {
            status = sink.transform(mediatingControl, source, event);
        }
        return status;
    }

    private static final class MediatingController implements JsonController
    {
        private JsonController delegate;
        private boolean synthetic;

        @Override
        public void segmentable()
        {
        }

        @Override
        public void verbatim()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            if (!synthetic)
            {
                delegate.consumed(sourceBytes);
            }
        }
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
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
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
