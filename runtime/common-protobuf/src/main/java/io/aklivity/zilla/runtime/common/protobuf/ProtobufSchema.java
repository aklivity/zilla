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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufDiscardSinkImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufParserImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufStreamImpl;
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
    private final Map<String, int[]> indexesByRecord;
    private final Map<String, ProtobufMessage> messageByIndexes;

    private ProtobufSchema(
        Map<String, ProtobufMessage> messages,
        Map<String, ProtobufEnum> enums,
        List<ProtobufMessage> ordered)
    {
        this.messages = messages;
        this.enums = enums;
        this.indexesByRecord = new LinkedHashMap<>();
        this.messageByIndexes = new LinkedHashMap<>();
        index(ordered);
    }

    /**
     * The schema-registry message-index path (declaration-order indices from the file's top-level
     * messages down through nested types, synthetic {@code map_entry} messages ordered after explicit
     * nested types as {@code protoc} emits them) for the message named {@code record} — accepted either
     * as a package-relative dotted name (e.g. {@code Outer.Inner}) or as a dotless full name. Returns
     * {@code null} when no such message exists.
     */
    public int[] messageIndexes(
        String record)
    {
        int[] indexes = indexesByRecord.get(record);
        return indexes != null ? indexes.clone() : null;
    }

    /**
     * The message at the given schema-registry message-index path, or {@code null} when the path does
     * not resolve to a message in this schema.
     */
    public ProtobufMessage messageByIndexes(
        int[] indexes)
    {
        return indexes != null ? messageByIndexes.get(Arrays.toString(indexes)) : null;
    }

    private void index(
        List<ProtobufMessage> ordered)
    {
        Map<String, List<ProtobufMessage>> childrenOf = new LinkedHashMap<>();
        List<ProtobufMessage> roots = new ArrayList<>();
        for (ProtobufMessage message : ordered)
        {
            int dot = message.name().lastIndexOf('.');
            String parent = dot < 0 ? null : message.name().substring(0, dot);
            if (parent != null && messages.containsKey(parent))
            {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(message);
            }
            else
            {
                roots.add(message);
            }
        }
        index(roots, new int[0], "", childrenOf);
    }

    private void index(
        List<ProtobufMessage> siblings,
        int[] prefix,
        String simplePrefix,
        Map<String, List<ProtobufMessage>> childrenOf)
    {
        List<ProtobufMessage> sorted = new ArrayList<>(siblings.size());
        for (ProtobufMessage sibling : siblings)
        {
            if (!sibling.mapEntry())
            {
                sorted.add(sibling);
            }
        }
        for (ProtobufMessage sibling : siblings)
        {
            if (sibling.mapEntry())
            {
                sorted.add(sibling);
            }
        }

        for (int i = 0; i < sorted.size(); i++)
        {
            ProtobufMessage message = sorted.get(i);
            int[] path = Arrays.copyOf(prefix, prefix.length + 1);
            path[prefix.length] = i;
            int dot = message.name().lastIndexOf('.');
            String simpleName = dot < 0 ? message.name() : message.name().substring(dot + 1);
            String simplePath = simplePrefix.isEmpty() ? simpleName : simplePrefix + "." + simpleName;
            indexesByRecord.put(message.name(), path);
            indexesByRecord.put(simplePath, path);
            messageByIndexes.put(Arrays.toString(path), message);
            index(childrenOf.getOrDefault(message.name(), List.of()), path, simplePath, childrenOf);
        }
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
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        ProtobufPipeline pipeline = new ProtobufStreamImpl(new ProtobufParserImpl(this, messageName))
            .transform(new ProtobufValidatorImpl(this, messageName))
            .into(new ProtobufDiscardSinkImpl());
        pipeline.reset();
        return pipeline.transform(buffer, offset, offset + length) == ProtobufPipeline.Status.COMPLETED;
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
            Map<String, ProtobufMessage> resolvedMessages = Map.copyOf(messages);
            Map<String, ProtobufEnum> resolvedEnums = Map.copyOf(enums);
            for (ProtobufMessage message : resolvedMessages.values())
            {
                for (ProtobufField field : message.fields())
                {
                    if (field.composite())
                    {
                        field.resolve(resolvedMessages.get(field.typeName()));
                    }
                    else if (field.type() == ProtobufType.ENUM)
                    {
                        field.resolve(resolvedEnums.get(field.typeName()));
                    }
                }
            }
            return new ProtobufSchema(resolvedMessages, resolvedEnums, new ArrayList<>(messages.values()));
        }
    }
}
