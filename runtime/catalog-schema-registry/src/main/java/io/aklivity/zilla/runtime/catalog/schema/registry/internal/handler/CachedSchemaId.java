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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedSchemaId
{
    public static final int PLACEHOLDER_SCHEMA_ID = -1;
    public static final CachedSchemaId IN_PROGRESS = new CachedSchemaId(Long.MAX_VALUE, PLACEHOLDER_SCHEMA_ID,
        new AtomicInteger(), Long.MAX_VALUE);

    public long timestamp;
    public int id;
    public final AtomicInteger retryAttempts;
    public final long retryAfter;

    public CachedSchemaId(
        long timestamp,
        int id,
        AtomicInteger retryAttempts,
        long retryAfter)
    {
        this.timestamp = timestamp;
        this.id = id;
        this.retryAttempts = retryAttempts;
        this.retryAfter = retryAfter;
    }

    public boolean expired(
        long maxAgeMillis)
    {
        return System.currentTimeMillis() - this.timestamp > maxAgeMillis;
    }

    public boolean retry()
    {
        return System.currentTimeMillis() - this.timestamp > this.retryAfter;
    }
}
