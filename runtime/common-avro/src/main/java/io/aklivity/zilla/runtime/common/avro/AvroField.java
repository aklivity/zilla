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
package io.aklivity.zilla.runtime.common.avro;

import java.util.List;

import jakarta.json.JsonValue;

/**
 * An immutable record field descriptor: its declared name, {@link AvroType}, aliases, and default value.
 * Obtained from {@link AvroType#fields()}.
 */
public interface AvroField
{
    String name();

    AvroType type();

    /**
     * The field aliases, for rename-tolerant schema resolution; empty if none are declared.
     */
    List<String> aliases();

    /**
     * The field's default as a JSON value, used to populate the field during schema resolution when a
     * writer omitted it. {@code null} when no default is declared; {@link JsonValue#NULL} for an explicit
     * null default (distinguishing the two).
     */
    JsonValue defaultValue();
}
