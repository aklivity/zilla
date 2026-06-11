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
package io.aklivity.zilla.runtime.common.protobuf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufDiscardSinkImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufPipelineImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufValidatorImpl;

/**
 * A compiled, immutable Protobuf model: a registry of {@link ProtobufMessage} and
 * {@link ProtobufEnum} descriptors keyed by full name. Build one per {@code schemaId} and cache
 * it; resolution of composite field references is by full name against this registry.
 */
public final class ProtobufSchema
{
    private final Map<String, ProtobufMessage> messages;
    private final Map<String, ProtobufEnum> enums;

    private ProtobufSchema(
        Map<String, ProtobufMessage> messages,
        Map<String, ProtobufEnum> enums)
    {
        this.messages = messages;
        this.enums = enums;
    }

    public ProtobufMessage message(
        String name)
    {
        return messages.get(name);
    }

    public ProtobufEnum enumeration(
        String name)
    {
        return enums.get(name);
    }

    public ProtobufMessage resolveMessage(
        ProtobufField field)
    {
        ProtobufMessage message = field.composite() ? messages.get(field.typeName()) : null;
        if (field.composite() && message == null)
        {
            throw new ProtobufException("unresolved message type " + field.typeName());
        }
        return message;
    }

    public ProtobufEnum resolveEnum(
        ProtobufField field)
    {
        ProtobufEnum enumeration = field.type() == ProtobufType.ENUM ? enums.get(field.typeName()) : null;
        if (field.type() == ProtobufType.ENUM && enumeration == null)
        {
            throw new ProtobufException("unresolved enum type " + field.typeName());
        }
        return enumeration;
    }

    /**
     * A streaming {@link ProtobufTransform} that validates the event stream of the message named
     * {@code messageName} against this schema while forwarding every event unchanged. Its
     * {@link ProtobufPipeline.Status} carries the verdict — {@link ProtobufPipeline.Status#REJECTED}
     * (emit-then-abort) when a proto2 {@code required} field is missing.
     */
    public ProtobufTransform validator(
        String messageName)
    {
        return new ProtobufValidatorImpl(this, messageName);
    }

    /**
     * One-shot validation of a fully-buffered message named {@code messageName} against this schema:
     * returns {@code true} when the wire decodes structurally and every proto2 {@code required} field
     * is present. A convenience over the pipeline (it builds a per-call validating pipeline); for
     * repeated validation on the hot path, build a pipeline once with {@link #validator(String)} and
     * reuse it.
     */
    public boolean validate(
        String messageName,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        ProtobufPipeline pipeline = new ProtobufPipelineImpl(this, messageName,
            List.of(new ProtobufValidatorImpl(this, messageName)), new ProtobufDiscardSinkImpl());
        pipeline.reset();
        return pipeline.feed(buffer, offset, length) == ProtobufPipeline.Status.COMPLETE;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private final Map<String, ProtobufMessage> messages;
        private final Map<String, ProtobufEnum> enums;

        private Builder()
        {
            this.messages = new LinkedHashMap<>();
            this.enums = new LinkedHashMap<>();
        }

        public Builder message(
            ProtobufMessage message)
        {
            messages.put(message.name(), message);
            return this;
        }

        public Builder enumeration(
            ProtobufEnum enumeration)
        {
            enums.put(enumeration.name(), enumeration);
            return this;
        }

        public ProtobufSchema build()
        {
            return new ProtobufSchema(Map.copyOf(messages), Map.copyOf(enums));
        }
    }
}
