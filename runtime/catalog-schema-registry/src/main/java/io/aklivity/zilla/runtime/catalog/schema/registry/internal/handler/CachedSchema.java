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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedSchema
{
    public static final String SCHEMA_PLACEHOLDER = "schema";
    public static final CachedSchema IN_PROGRESS = new CachedSchema(SCHEMA_PLACEHOLDER);

    public final String schema;
    public final AtomicInteger retryAttempts;

    public CachedSchema(
        String schema)
    {
        this.schema = schema;
        this.retryAttempts = new AtomicInteger();
    }

    public CachedSchema(
        String schema,
        AtomicInteger retryAttempts)
    {
        this.schema = schema;
        this.retryAttempts = retryAttempts;
    }
}
