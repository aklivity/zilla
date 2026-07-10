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
import java.util.function.Predicate;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Partitions items in a JSON-RPC list response into eager (copied through verbatim) and cold, per an
 * operator-configured eager policy. A cold item is either annotated with a synthetic
 * {@code "defer_loading":true} member (when {@code omitCold} is {@code false}, so it remains discoverable
 * in the response) or dropped entirely (when {@code omitCold} is {@code true}, so it is reachable only via
 * a companion search tool). Assumes {@code name} is the first member of each item object, matching
 * {@link McpScopeFilter}.
 */
public final class McpEagerFilter implements JsonTransform
{
    private static final String NAME_KEY = "name";
    private static final String DEFER_LOADING_KEY = "defer_loading";

    @FunctionalInterface
    private interface State
    {
        Status apply(
            JsonSource source,
            JsonEvent event,
            JsonSink sink);
    }

    private String arrayKey;
    private Predicate<CharSequence> eager;
    private boolean omitCold;
    private final KeySource keySource = new KeySource();
    private final Control mediator = new Control();
    private final Control inject = new Control(true);

    private final State outer = this::onOuter;
    private final State items = this::onItems;
    private final State pending = this::onPending;
    private final State copying = this::onCopying;
    private final State skipping = this::onSkipping;

    private State state = outer;
    private int depth;
    private int itemDepth;
    private boolean itemsArmed;
    private boolean nameArmed;

    public void init(
        String arrayKey,
        Predicate<CharSequence> eager,
        boolean omitCold)
    {
        this.arrayKey = arrayKey;
        this.eager = eager;
        this.omitCold = omitCold;
        reset();
    }

    @Override
    public void reset()
    {
        state = outer;
        depth = 0;
        itemDepth = 0;
        itemsArmed = false;
        nameArmed = false;
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
        Status status;
        if (event == JsonEvent.KEY_NAME)
        {
            status = onOuterKey(source, sink);
        }
        else
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
            default:
                break;
            }
            status = sink.transform(mediator, source, event);
        }
        return status;
    }

    // the arm key is forwarded either way (every outer key passes through unconditionally), so a fragmented
    // key is declined until complete before the arm decision and forward, rather than deciding on a prefix
    private Status onOuterKey(
        JsonSource source,
        JsonSink sink)
    {
        Status status;
        if (depth == 1 && source.deferredBytes())
        {
            mediator.delegate.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            if (depth == 1)
            {
                itemsArmed = arrayKey.contentEquals(source.getStringView());
            }
            status = sink.transform(mediator, source, JsonEvent.KEY_NAME);
        }
        return status;
    }

    // between items in the target array: each item object defers its emission until its name resolves
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
            state = pending;
            status = Status.ADVANCED;
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

    // inside an item whose eager/cold disposition is undecided: swallow events until the name value is seen
    private Status onPending(
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
        case END_OBJECT:
            itemDepth--;
            status = sink.transform(mediator, source, event);
            if (itemDepth == 0)
            {
                state = items;
            }
            break;
        case END_ARRAY:
            itemDepth--;
            status = sink.transform(mediator, source, event);
            break;
        case KEY_NAME:
            if (itemDepth == 1)
            {
                status = onNameKey(source);
            }
            else
            {
                status = sink.transform(mediator, source, event);
            }
            break;
        case VALUE_STRING:
            if (nameArmed && source.deferredBytes())
            {
                // eager.test() needs the whole name value; decline the fragment so the source accumulates
                // it whole and re-presents it complete on a later window, then resolve once total.
                mediator.delegate.consumed(0);
                status = Status.STARVED;
            }
            else
            {
                status = nameArmed
                    ? resolveItem(source, event, sink)
                    : sink.transform(mediator, source, event);
            }
            break;
        default:
            status = Status.ADVANCED;
            break;
        }
        return status;
    }

    // inside a kept item: copy its remaining content through verbatim
    private Status onCopying(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            itemDepth++;
            break;
        case END_ARRAY:
            itemDepth--;
            break;
        case END_OBJECT:
            itemDepth--;
            if (itemDepth == 0)
            {
                state = items;
            }
            break;
        default:
            break;
        }
        return sink.transform(mediator, source, event);
    }

    // inside a cold, omitted item: swallow its remaining content
    private Status onSkipping(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            itemDepth++;
            break;
        case END_ARRAY:
            itemDepth--;
            break;
        case END_OBJECT:
            itemDepth--;
            if (itemDepth == 0)
            {
                state = items;
            }
            break;
        default:
            break;
        }
        return Status.ADVANCED;
    }

    // the key is swallowed either way (a synthetic "name" key replaces it once resolveItem() fires, or it
    // is simply dropped if some other key preceded "name"), so there is no forwarded content whose byte
    // fidelity a fragmented key could compromise -- decline the fragment so the source accumulates it whole
    // and re-presents it complete on a later window, then decide once total.
    private Status onNameKey(
        JsonSource source)
    {
        Status status;
        if (source.deferredBytes())
        {
            mediator.delegate.consumed(0);
            status = Status.STARVED;
        }
        else
        {
            nameArmed = NAME_KEY.contentEquals(source.getStringView());
            status = Status.ADVANCED;
        }
        return status;
    }

    private Status resolveItem(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final CharSequence name = source.getStringView();
        final boolean admitted = eager.test(name);

        nameArmed = false;

        Status status;
        if (!admitted && omitCold)
        {
            state = skipping;
            status = Status.ADVANCED;
        }
        else
        {
            // re-emit the elided object start and name key as synthetic events carrying no source bytes,
            // then resume the real name value through the byte-tracking mediator
            status = sink.transform(inject, source, JsonEvent.START_OBJECT);
            if (status == Status.ADVANCED)
            {
                status = sink.transform(inject, keySource.with(NAME_KEY), JsonEvent.KEY_NAME);
            }
            if (status == Status.ADVANCED)
            {
                status = sink.transform(mediator, source, event);
            }
            if (status == Status.ADVANCED && !admitted)
            {
                status = sink.transform(inject, keySource.with(DEFER_LOADING_KEY), JsonEvent.KEY_NAME);
                if (status == Status.ADVANCED)
                {
                    status = sink.transform(inject, keySource, JsonEvent.VALUE_TRUE);
                }
            }
            state = copying;
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
