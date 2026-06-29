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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// drops the securitySchemes member from each element of the target array, forwarding everything else
// unchanged; used at the mcp server egress to withhold securitySchemes from clients until SEP-1488 finalizes,
// while the upstream pipeline keeps it for scope enforcement
public final class McpSchemeExcluder implements JsonTransform
{
    private static final String SECURITY_SCHEMES = "securitySchemes";

    @FunctionalInterface
    private interface State
    {
        Status apply(
            JsonSource source,
            JsonEvent event,
            JsonSink sink);
    }

    private final String arrayKey;
    private final Control mediator = new Control();

    private final State outer = this::onOuter;
    private final State items = this::onItems;
    private final State element = this::onElement;
    private final State skipping = this::onSkipping;

    private State state = outer;
    private int depth;
    private int itemDepth;
    private int skipDepth;
    private boolean itemsArmed;

    public McpSchemeExcluder(
        String arrayKey)
    {
        this.arrayKey = arrayKey;
    }

    @Override
    public void reset()
    {
        state = outer;
        depth = 0;
        itemDepth = 0;
        skipDepth = 0;
        itemsArmed = false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        mediator.delegate = control;
        return state.apply(source, event, sink);
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

    // between elements of the target array: a direct child object becomes a filtered element
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

    // inside an element object: forward everything except the securitySchemes member
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
        case END_OBJECT:
            itemDepth--;
            status = sink.transform(mediator, source, event);
            if (itemDepth == 0)
            {
                state = items;
            }
            break;
        case KEY_NAME:
            if (itemDepth == 1 && SECURITY_SCHEMES.contentEquals(source.getStringView()))
            {
                skipDepth = 0;
                state = skipping;
                status = Status.ADVANCED;
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

    // swallow the securitySchemes value, whether a nested structure or a scalar, then resume the element
    private Status onSkipping(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            skipDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            skipDepth--;
            if (skipDepth == 0)
            {
                state = element;
            }
            break;
        default:
            if (skipDepth == 0)
            {
                state = element;
            }
            break;
        }
        return Status.ADVANCED;
    }

    private static final class Control implements JsonController
    {
        private JsonController delegate;

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
            delegate.consumed(sourceBytes);
        }
    }
}
