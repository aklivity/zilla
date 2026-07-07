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
import java.util.function.Function;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

public final class McpSchemeInjector implements JsonTransform
{
    private static final String SECURITY_SCHEMES = "securitySchemes";
    private static final String TYPE = "type";
    private static final String OAUTH2 = "oauth2";
    private static final String SCOPES = "scopes";

    @FunctionalInterface
    private interface State
    {
        Status apply(
            JsonSource source,
            JsonEvent event,
            JsonSink sink);
    }

    private final String arrayKey;
    private final boolean active;
    private final Function<String, Map<String, List<String>>> rolesByTool;
    private final TextSource text = new TextSource();
    private final Control mediator = new Control();
    private final Control inject = new Control(true);

    private final State outer = this::onOuter;
    private final State items = this::onItems;
    private final State element = this::onElement;

    private State state = outer;
    private int depth;
    private int itemDepth;
    private boolean itemsArmed;
    private boolean nameArmed;
    private Map<String, List<String>> itemRoles = Map.of();

    public McpSchemeInjector(
        String arrayKey,
        boolean active,
        Function<String, Map<String, List<String>>> rolesByTool)
    {
        this.arrayKey = arrayKey;
        this.active = active;
        this.rolesByTool = rolesByTool;
    }

    @Override
    public void reset()
    {
        state = outer;
        depth = 0;
        itemDepth = 0;
        itemsArmed = false;
        nameArmed = false;
        itemRoles = Map.of();
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        mediator.delegate = control;
        return active
            ? state.apply(source, event, sink)
            : sink.transform(control, source, event);
    }

    // outside the target array: pass everything through, arming when the target array key is seen
    private Status onOuter(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            if (itemsArmed && event == JsonEvent.START_ARRAY)
            {
                itemsArmed = false;
                itemDepth = 0;
                state = items;
            }
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            itemsArmed = depth == 1 && arrayKey.contentEquals(source.getStringView());
            break;
        default:
            break;
        }
        return sink.transform(mediator, source, event);
    }

    // between elements of the target array: a direct child object becomes an injection element
    private Status onItems(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_OBJECT:
            itemDepth = 1;
            nameArmed = false;
            itemRoles = Map.of();
            state = element;
            status = sink.transform(mediator, source, event);
            break;
        case END_ARRAY:
            state = outer;
            status = sink.transform(mediator, source, event);
            break;
        default:
            status = sink.transform(mediator, source, event);
            break;
        }
        return status;
    }

    // inside a direct element object: resolve the item's own securitySchemes by its "name" field,
    // then inject them (if any) just before the item's closing brace
    private Status onElement(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            itemDepth++;
            status = sink.transform(mediator, source, event);
            break;
        case END_ARRAY:
            itemDepth--;
            status = sink.transform(mediator, source, event);
            break;
        case KEY_NAME:
            nameArmed = itemDepth == 1 && "name".contentEquals(source.getStringView());
            status = sink.transform(mediator, source, event);
            break;
        case VALUE_STRING:
            if (nameArmed)
            {
                itemRoles = rolesByTool.apply(source.getString());
                nameArmed = false;
            }
            status = sink.transform(mediator, source, event);
            break;
        case END_OBJECT:
            itemDepth--;
            if (itemDepth == 0)
            {
                status = itemRoles.isEmpty() ? Status.ADVANCED : injectSchemes(sink, List.copyOf(itemRoles.values()));
                if (status != Status.REJECTED)
                {
                    status = sink.transform(mediator, source, event);
                }
                state = items;
            }
            else
            {
                status = sink.transform(mediator, source, event);
            }
            break;
        default:
            status = sink.transform(mediator, source, event);
            break;
        }
        return status;
    }

    // emit "securitySchemes":[{"type":"oauth2","scopes":[...]}, ...] as structured events (carrying no source bytes)
    private Status injectSchemes(
        JsonSink sink,
        List<List<String>> schemes)
    {
        Status status = sink.transform(inject, text.with(SECURITY_SCHEMES), JsonEvent.KEY_NAME);
        if (status != Status.REJECTED)
        {
            status = sink.transform(inject, text, JsonEvent.START_ARRAY);
        }
        for (int i = 0; status != Status.REJECTED && i < schemes.size(); i++)
        {
            final List<String> scopes = schemes.get(i);
            status = sink.transform(inject, text, JsonEvent.START_OBJECT);
            if (status != Status.REJECTED)
            {
                status = sink.transform(inject, text.with(TYPE), JsonEvent.KEY_NAME);
            }
            if (status != Status.REJECTED)
            {
                status = sink.transform(inject, text.with(OAUTH2), JsonEvent.VALUE_STRING);
            }
            if (status != Status.REJECTED)
            {
                status = sink.transform(inject, text.with(SCOPES), JsonEvent.KEY_NAME);
            }
            if (status != Status.REJECTED)
            {
                status = sink.transform(inject, text, JsonEvent.START_ARRAY);
            }
            for (int j = 0; status != Status.REJECTED && j < scopes.size(); j++)
            {
                status = sink.transform(inject, text.with(scopes.get(j)), JsonEvent.VALUE_STRING);
            }
            if (status != Status.REJECTED)
            {
                status = sink.transform(inject, text, JsonEvent.END_ARRAY);
            }
            if (status != Status.REJECTED)
            {
                status = sink.transform(inject, text, JsonEvent.END_OBJECT);
            }
        }
        if (status != Status.REJECTED)
        {
            status = sink.transform(inject, text, JsonEvent.END_ARRAY);
        }
        return status;
    }

    private static final class Control implements JsonController
    {
        private final boolean synthetic;

        private JsonController delegate;

        private Control()
        {
            this(false);
        }

        private Control(
            boolean synthetic)
        {
            this.synthetic = synthetic;
        }

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

    private static final class TextSource implements JsonSource
    {
        private CharSequence text;

        private TextSource with(
            CharSequence text)
        {
            this.text = text;
            return this;
        }

        @Override
        public String getString()
        {
            return text == null ? null : text.toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return text;
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
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
    }
}
