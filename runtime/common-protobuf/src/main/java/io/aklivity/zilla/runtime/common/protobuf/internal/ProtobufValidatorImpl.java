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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;

/**
 * A {@link ProtobufTransform} that validates the event stream against the descriptor while forwarding
 * every event unchanged. The driver already rejects malformed wire and wire-type/declared-type
 * mismatches; this stage adds descriptor-level semantic validation — proto2 {@code required}-field
 * presence per message scope — and reports failure at the message boundary, after the events are
 * forwarded, so callers abort on {@link ProtobufPipeline.Status#REJECTED} (emit-then-abort).
 */
public final class ProtobufValidatorImpl implements ProtobufTransform
{
    private final ProtobufSchema schema;
    private final String messageName;
    private final List<Scope> scopes;

    private int depth;
    private ProtobufField pendingField;

    public ProtobufValidatorImpl(
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.scopes = new ArrayList<>();
        this.depth = -1;
    }

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event,
        ProtobufSink sink)
    {
        ProtobufPipeline.Status status = sink.feed(control, source, event);
        switch (event)
        {
        case START_MESSAGE:
        case START_GROUP:
            depth++;
            ProtobufMessage message = depth == 0
                ? schema.message(messageName)
                : schema.resolveMessage(pendingField);
            scope(depth).reset(message);
            break;
        case FIELD:
            pendingField = source.field();
            scope(depth).see(source.field().number());
            break;
        case END_MESSAGE:
        case END_GROUP:
            if (status != ProtobufPipeline.Status.REJECTED && !scope(depth).satisfied())
            {
                status = ProtobufPipeline.Status.REJECTED;
            }
            depth--;
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = -1;
        pendingField = null;
    }

    private Scope scope(
        int depth)
    {
        while (scopes.size() <= depth)
        {
            scopes.add(new Scope());
        }
        return scopes.get(depth);
    }

    private static final class Scope
    {
        private List<ProtobufField> required;
        private boolean[] seen;
        private int count;

        private void reset(
            ProtobufMessage message)
        {
            required = message.requiredFields();
            count = required.size();
            if (seen == null || seen.length < count)
            {
                seen = new boolean[Math.max(count, 4)];
            }
            Arrays.fill(seen, 0, count, false);
        }

        private void see(
            int number)
        {
            for (int i = 0; i < count; i++)
            {
                if (required.get(i).number() == number)
                {
                    seen[i] = true;
                }
            }
        }

        private boolean satisfied()
        {
            boolean satisfied = true;
            for (int i = 0; i < count; i++)
            {
                satisfied &= seen[i];
            }
            return satisfied;
        }
    }
}
