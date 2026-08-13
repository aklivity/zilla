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
}
