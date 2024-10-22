/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;


public abstract class RisingwaveCommandTemplate
{
    protected static final String ZILLA_CORRELATION_ID = "zilla_correlation_id";
    protected static final String ZILLA_IDENTITY = "zilla_identity";
    protected static final String ZILLA_TIMESTAMP = "zilla_timestamp";

    protected final StringBuilder fieldBuilder = new StringBuilder();
    protected final StringBuilder includeBuilder = new StringBuilder();

    protected static final Map<String, String> ZILLA_MAPPINGS = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_MAPPINGS.put(ZILLA_CORRELATION_ID, "INCLUDE header 'zilla:correlation-id' AS %s\n");
        ZILLA_MAPPINGS.put(ZILLA_IDENTITY, "INCLUDE header 'zilla:identity' AS %s\n");
        ZILLA_MAPPINGS.put(ZILLA_TIMESTAMP, "INCLUDE timestamp AS %s\n");
    }

    protected static final Map<String, String> ZILLA_INCLUDE_TYPE_MAPPINGS = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_INCLUDE_TYPE_MAPPINGS.put(ZILLA_CORRELATION_ID, "VARCHAR");
        ZILLA_INCLUDE_TYPE_MAPPINGS.put(ZILLA_IDENTITY, "VARCHAR");
        ZILLA_INCLUDE_TYPE_MAPPINGS.put(ZILLA_TIMESTAMP, "TIMESTAMP");
    }
}
