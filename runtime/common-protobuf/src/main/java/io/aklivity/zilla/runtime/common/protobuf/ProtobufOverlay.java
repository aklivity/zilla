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
package io.aklivity.zilla.runtime.common.protobuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Resolves a friendly {@code method:}/{@code field:} reference against a {@link ProtobufSchema} and
 * merges its {@code options:} bag onto the referenced node's existing options, via {@link
 * jakarta.json.JsonMergePatch}. {@code method:} addresses {@code package.Service/MethodName}, with a
 * {@code *} wildcard matched anywhere in the string; {@code field:} addresses the exact, non-wildcarded
 * {@code package.Message.field_name}.
 * <p>
 * One {@link ProtobufOverlay} is meant to be reused across many schemas: an entry whose reference does
 * not resolve against a particular schema is silently skipped for that schema rather than rejected, and
 * {@link #apply(ProtobufSchema)} returns the identical input instance, unmodified, when nothing in this
 * overlay matches anything in it. When something does match, only the touched messages/services are
 * rebuilt — every other message, enum, service, and method is carried into the result by reference,
 * unmodified and unshared with any other {@link ProtobufSchema} instance.
 */
public final class ProtobufOverlay
{
    private final List<Entry> entries;

    private ProtobufOverlay(
        List<Entry> entries)
    {
        this.entries = entries;
    }

    public static ProtobufOverlay of(
        JsonValue resolved)
    {
        JsonArray array = resolved.asJsonArray();
        List<Entry> entries = new ArrayList<>(array.size());
        for (JsonValue value : array)
        {
            JsonObject object = value.asJsonObject();
            boolean hasMethod = object.containsKey("method");
            boolean hasField = object.containsKey("field");
            if (hasMethod == hasField)
            {
                throw new IllegalArgumentException(
                    "Overlay entry must declare exactly one of \"method\" or \"field\"");
            }
            if (!object.containsKey("options"))
            {
                throw new IllegalArgumentException("Overlay entry missing required \"options\" field");
            }
            JsonObject options = object.getJsonObject("options");
            entries.add(hasMethod
                ? new Entry(asPattern(object.getString("method")), null, options)
                : new Entry(null, object.getString("field"), options));
        }
        return new ProtobufOverlay(entries);
    }

    public ProtobufSchema apply(
        ProtobufSchema schema)
    {
        Map<String, Map<String, JsonObject>> fieldOverlaysByMessage = new LinkedHashMap<>();
        Map<String, Map<String, JsonObject>> methodOverlaysByService = new LinkedHashMap<>();

        for (Entry entry : entries)
        {
            if (entry.method != null)
            {
                matchMethod(schema, entry, methodOverlaysByService);
            }
            else
            {
                matchField(schema, entry, fieldOverlaysByMessage);
            }
        }

        ProtobufSchema result = schema;
        if (!fieldOverlaysByMessage.isEmpty() || !methodOverlaysByService.isEmpty())
        {
            result = rebuild(schema, fieldOverlaysByMessage, methodOverlaysByService);
        }
        return result;
    }

    private static void matchMethod(
        ProtobufSchema schema,
        Entry entry,
        Map<String, Map<String, JsonObject>> methodOverlaysByService)
    {
        for (ProtobufService service : schema.services())
        {
            for (ProtobufMethod method : service.methods())
            {
                if (entry.method.matcher(service.name() + "/" + method.name()).matches())
                {
                    Map<String, JsonObject> byMethod =
                        methodOverlaysByService.computeIfAbsent(service.name(), k -> new LinkedHashMap<>());
                    JsonObject base = byMethod.getOrDefault(method.name(), method.options());
                    byMethod.put(method.name(), merge(base, entry.options));
                }
            }
        }
    }

    private static void matchField(
        ProtobufSchema schema,
        Entry entry,
        Map<String, Map<String, JsonObject>> fieldOverlaysByMessage)
    {
        int dot = entry.field.lastIndexOf('.');
        if (dot > 0)
        {
            String messageName = entry.field.substring(0, dot);
            String fieldName = entry.field.substring(dot + 1);
            ProtobufMessage message = schema.message(messageName);
            ProtobufField field = message != null ? message.field(fieldName) : null;
            if (field != null)
            {
                Map<String, JsonObject> byField =
                    fieldOverlaysByMessage.computeIfAbsent(messageName, k -> new LinkedHashMap<>());
                JsonObject base = byField.getOrDefault(fieldName, field.options());
                byField.put(fieldName, merge(base, entry.options));
            }
        }
    }

    private static ProtobufSchema rebuild(
        ProtobufSchema schema,
        Map<String, Map<String, JsonObject>> fieldOverlaysByMessage,
        Map<String, Map<String, JsonObject>> methodOverlaysByService)
    {
        ProtobufSchema.Builder builder = ProtobufSchema.builder();
        for (ProtobufEnum enumeration : schema.enumerations())
        {
            builder.enumeration(enumeration);
        }
        for (ProtobufMessage message : schema.messages())
        {
            Map<String, JsonObject> overlaysByField = fieldOverlaysByMessage.get(message.name());
            if (overlaysByField != null)
            {
                builder.message(withOverlay(message, overlaysByField));
            }
            else
            {
                builder.reuse(message);
            }
        }
        for (ProtobufService service : schema.services())
        {
            Map<String, JsonObject> overlaysByMethod = methodOverlaysByService.get(service.name());
            builder.service(overlaysByMethod != null ? withOverlay(service, overlaysByMethod) : service);
        }
        return builder.build();
    }

    private static ProtobufMessage withOverlay(
        ProtobufMessage message,
        Map<String, JsonObject> overlaysByField)
    {
        ProtobufMessage.Builder builder = ProtobufMessage.builder(message.name()).mapEntry(message.mapEntry())
            .options(message.rawOptions());
        for (ProtobufField field : message.fields())
        {
            builder.field(copyField(field, overlaysByField.get(field.name())));
        }
        return builder.build();
    }

    private static ProtobufField copyField(
        ProtobufField field,
        JsonObject overlay)
    {
        ProtobufField.Builder builder = ProtobufField.builder()
            .number(field.number())
            .name(field.name())
            .jsonName(field.jsonName())
            .type(field.type())
            .repeated(field.repeated())
            .required(field.required())
            .packed(field.packed())
            .proto3Optional(field.proto3Optional())
            .options(field.rawOptions());
        if (field.typeName() != null)
        {
            builder.typeName(field.typeName());
        }
        if (field.oneofName() != null)
        {
            builder.oneof(field.oneofName());
        }
        if (field.defaultValue() != null)
        {
            builder.defaultValue(field.defaultValue());
        }
        if (overlay != null)
        {
            builder.overlay(overlay);
        }
        return builder.build();
    }

    private static ProtobufService withOverlay(
        ProtobufService service,
        Map<String, JsonObject> overlaysByMethod)
    {
        ProtobufService.Builder builder = ProtobufService.builder(service.name());
        for (ProtobufMethod method : service.methods())
        {
            JsonObject overlay = overlaysByMethod.get(method.name());
            builder.method(overlay != null ? copyMethod(method, overlay) : method);
        }
        return builder.build();
    }

    private static ProtobufMethod copyMethod(
        ProtobufMethod method,
        JsonObject overlay)
    {
        return ProtobufMethod.builder()
            .name(method.name())
            .inputType(method.inputType())
            .outputType(method.outputType())
            .clientStreaming(method.clientStreaming())
            .serverStreaming(method.serverStreaming())
            .options(method.rawOptions())
            .overlay(overlay)
            .build();
    }

    private static JsonObject merge(
        JsonObject existing,
        JsonObject patch)
    {
        return Json.createMergePatch(patch).apply(existing).asJsonObject();
    }

    private static Pattern asPattern(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*"));
    }

    private static final class Entry
    {
        private final Pattern method;
        private final String field;
        private final JsonObject options;

        private Entry(
            Pattern method,
            String field,
            JsonObject options)
        {
            this.method = method;
            this.field = field;
            this.options = options;
        }
    }
}
