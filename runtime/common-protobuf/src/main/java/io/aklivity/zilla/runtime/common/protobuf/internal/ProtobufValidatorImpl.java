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

import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Deque<Scope> scopes;

    private ProtobufField pendingField;

    public ProtobufValidatorImpl(
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.scopes = new ArrayDeque<>();
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
            ProtobufMessage message = scopes.isEmpty()
                ? schema.message(messageName)
                : schema.resolveMessage(pendingField);
            scopes.push(new Scope(message));
            break;
        case FIELD:
            pendingField = source.field();
            scopes.peek().see(source.field().number());
            break;
        case END_MESSAGE:
            Scope scope = scopes.pop();
            if (status != ProtobufPipeline.Status.REJECTED && !scope.satisfied())
            {
                status = ProtobufPipeline.Status.REJECTED;
            }
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        scopes.clear();
        pendingField = null;
    }

    private static final class Scope
    {
        private final List<ProtobufField> required;
        private final boolean[] seen;

        private Scope(
            ProtobufMessage message)
        {
            this.required = message.requiredFields();
            this.seen = new boolean[required.size()];
        }

        private void see(
            int number)
        {
            for (int i = 0; i < required.size(); i++)
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
            for (boolean present : seen)
            {
                satisfied &= present;
            }
            return satisfied;
        }
    }
}
