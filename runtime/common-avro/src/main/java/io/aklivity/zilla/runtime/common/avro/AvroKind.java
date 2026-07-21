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

/**
 * The kind of an {@link AvroType} — the Avro type categories, discriminating the accessors that carry
 * meaning for a given type (for example {@link AvroType#fields()} for {@link #RECORD},
 * {@link AvroType#symbols()} for {@link #ENUM}, {@link AvroType#branches()} for {@link #UNION}).
 */
public enum AvroKind
{
    NULL,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BYTES,
    STRING,
    FIXED,
    ENUM,
    RECORD,
    ARRAY,
    MAP,
    UNION
}
