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

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * The value of a field option, parsed from the {@code .proto} source text (a text-format literal) rather
 * than resolved against an extension descriptor — no {@code .proto} import declares these custom options,
 * so only the shape of the written constant is available. A string constant is unquoted; a message-valued
 * constant ({@code { key: value ... }}) exposes its fields directly as a nested {@link MessageValue}
 * rather than as flattened text, so a consumer can navigate it without re-parsing.
 */
public sealed interface ProtobufConstant
{
    /**
     * A bare (unquoted) identifier, e.g. an enum value name referenced as an option's value.
     */
    record Identifier(String value) implements ProtobufConstant
    {
    }

    record IntegerValue(long value) implements ProtobufConstant
    {
    }

    record FloatValue(double value) implements ProtobufConstant
    {
    }

    /**
     * A string constant, already unquoted (the parse tree's {@code getText()} retains the surrounding
     * quotes).
     */
    record TextValue(String value) implements ProtobufConstant
    {
    }

    record BooleanValue(boolean value) implements ProtobufConstant
    {
    }

    /**
     * A message-valued (aggregate) constant, e.g. {@code (acme.meta) = { key: "value" }}.
     */
    record MessageValue(Map<String, ProtobufConstant> fields) implements ProtobufConstant
    {
    }

    /**
     * This constant converted to a {@link JsonValue}, so an inline {@code .proto} source option and a
     * {@link ProtobufOverlay} entry's {@code options:} bag can be compared and merged in one common
     * representation. A message-valued constant converts recursively; every other variant maps directly
     * to its JSON-native equivalent.
     */
    default JsonValue toJson()
    {
        return switch (this)
        {
        case Identifier value -> Json.createValue(value.value());
        case IntegerValue value -> Json.createValue(value.value());
        case FloatValue value -> Json.createValue(value.value());
        case TextValue value -> Json.createValue(value.value());
        case BooleanValue value -> value.value() ? JsonValue.TRUE : JsonValue.FALSE;
        case MessageValue value -> toJsonObject(value.fields());
        };
    }

    /**
     * Converts a field's or method's full option map ({@link ProtobufField#option(String)},
     * {@link ProtobufMethod#option(String)}) to a {@link JsonObject}, one key per option name, with a
     * custom/extension option's surrounding {@code ( ... )} stripped from its key — that punctuation is
     * only how the {@code .proto} grammar distinguishes an extension reference from a built-in option
     * name, not part of the option's logical (dotted) identity, and a {@link ProtobufOverlay} entry's
     * {@code options:} bag always addresses that identity unparenthesized (e.g. {@code "zilla.mcp.v1"},
     * never {@code "(zilla.mcp.v1)"}); stripping it here is what lets the two sides merge onto the same
     * key rather than coexist as unrelated siblings. A nested {@code MessageValue}'s own field names are
     * never parenthesized, so this is a no-op wherever it doesn't apply. {@code null} converts to an
     * empty object.
     */
    static JsonObject toJsonObject(
        Map<String, ProtobufConstant> options)
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (options != null)
        {
            options.forEach((name, value) -> builder.add(unparenthesize(name), value.toJson()));
        }
        return builder.build();
    }

    private static String unparenthesize(
        String name)
    {
        return name.length() >= 2 && name.charAt(0) == '(' && name.charAt(name.length() - 1) == ')'
            ? name.substring(1, name.length() - 1)
            : name;
    }
}
