/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.protobuf.json;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWellKnownTypes;

/**
 * Describes a message of a {@link ProtobufSchema} as a JSON Schema (2020-12) document for the proto3 JSON
 * mapping that {@link ProtobufJson} reads and writes: proto3 json names as property names, 64-bit integers as
 * strings, {@code bytes} as base64, enums as names, {@code map} as an object, {@code repeated} as an array and
 * the well-known wrapper messages as their underlying scalar. Only proto2 {@code required} fields are listed
 * as required.
 * <p>
 * A message is expanded inline where it is used; a message that contains itself, directly or through other
 * messages, is declared once under {@code $defs} and referenced with {@code $ref}.
 */
public final class ProtobufJsonSchema
{
    private static final String REF_PREFIX = "#/$defs/";

    private final ProtobufSchema schema;
    private final Deque<ProtobufMessage> visiting;
    private final Set<String> recursive;
    private final Map<String, JsonObject> definitions;

    public static JsonObject of(
        ProtobufSchema schema,
        String messageName)
    {
        final ProtobufMessage message = schema.message(messageName);
        if (message == null)
        {
            throw new ProtobufException("unresolved message type " + messageName);
        }

        return new ProtobufJsonSchema(schema).root(message);
    }

    private ProtobufJsonSchema(
        ProtobufSchema schema)
    {
        this.schema = schema;
        this.visiting = new ArrayDeque<>();
        this.recursive = new HashSet<>();
        this.definitions = new LinkedHashMap<>();
    }

    private JsonObject root(
        ProtobufMessage message)
    {
        final JsonObject described = describe(message);

        JsonObject result = described;
        if (!definitions.isEmpty())
        {
            final JsonObjectBuilder defs = Json.createObjectBuilder();
            definitions.forEach(defs::add);
            result = Json.createObjectBuilder(described).add("$defs", defs).build();
        }

        return result;
    }

    private JsonObject describe(
        ProtobufMessage message)
    {
        JsonObject result;

        if (visiting.contains(message))
        {
            recursive.add(message.name());
            result = reference(message);
        }
        else
        {
            visiting.push(message);
            final JsonObject body = object(message);
            visiting.pop();

            if (recursive.contains(message.name()))
            {
                definitions.put(message.name(), body);
                result = reference(message);
            }
            else
            {
                result = body;
            }
        }

        return result;
    }

    private JsonObject object(
        ProtobufMessage message)
    {
        final JsonObjectBuilder properties = Json.createObjectBuilder();
        for (ProtobufField field : message.fields())
        {
            properties.add(field.jsonName(), property(field));
        }

        final JsonObjectBuilder object = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", properties);

        if (!message.requiredFields().isEmpty())
        {
            final JsonArrayBuilder required = Json.createArrayBuilder();
            message.requiredFields().forEach(field -> required.add(field.jsonName()));
            object.add("required", required);
        }

        return object.build();
    }

    private JsonObject property(
        ProtobufField field)
    {
        JsonObject result;

        if (field.repeated() && mapEntry(field))
        {
            final ProtobufMessage entry = schema.resolveMessage(field);
            result = Json.createObjectBuilder()
                .add("type", "object")
                .add("additionalProperties", value(entry.mapValue()))
                .build();
        }
        else if (field.repeated())
        {
            result = Json.createObjectBuilder()
                .add("type", "array")
                .add("items", value(field))
                .build();
        }
        else
        {
            result = value(field);
        }

        return result;
    }

    private boolean mapEntry(
        ProtobufField field)
    {
        return field.composite() && !ProtobufWellKnownTypes.wrapper(field.typeName()) &&
            schema.resolveMessage(field).mapEntry();
    }

    private JsonObject value(
        ProtobufField field)
    {
        JsonObject result;

        if (ProtobufWellKnownTypes.wrapper(field.typeName()))
        {
            result = scalar(ProtobufWellKnownTypes.wrapperMessage(field.typeName()).field(1).type());
        }
        else if (field.composite())
        {
            result = describe(schema.resolveMessage(field));
        }
        else if (field.type() == ProtobufType.ENUM)
        {
            result = enumeration(schema.resolveEnum(field));
        }
        else
        {
            result = scalar(field.type());
        }

        return result;
    }

    private static JsonObject enumeration(
        ProtobufEnum enumeration)
    {
        final JsonArrayBuilder names = Json.createArrayBuilder();
        enumeration.names().forEach(names::add);

        return Json.createObjectBuilder()
            .add("type", "string")
            .add("enum", names)
            .build();
    }

    private static JsonObject scalar(
        ProtobufType type)
    {
        final JsonObjectBuilder scalar = Json.createObjectBuilder();

        switch (type)
        {
        case DOUBLE:
        case FLOAT:
            scalar.add("type", "number");
            break;
        case INT32:
        case SINT32:
        case SFIXED32:
            scalar.add("type", "integer");
            break;
        case UINT32:
        case FIXED32:
            scalar.add("type", "integer").add("minimum", 0);
            break;
        case INT64:
        case SINT64:
        case SFIXED64:
            scalar.add("type", "string").add("pattern", "^-?[0-9]+$");
            break;
        case UINT64:
        case FIXED64:
            scalar.add("type", "string").add("pattern", "^[0-9]+$");
            break;
        case BOOL:
            scalar.add("type", "boolean");
            break;
        case BYTES:
            scalar.add("type", "string").add("contentEncoding", "base64");
            break;
        default:
            scalar.add("type", "string");
            break;
        }

        return scalar.build();
    }

    private static JsonObject reference(
        ProtobufMessage message)
    {
        return Json.createObjectBuilder()
            .add("$ref", REF_PREFIX + message.name())
            .build();
    }
}
