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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedArtifactId
{
    public static final int ID_PLACEHOLDER = -1;
    public static final CachedArtifactId IN_PROGRESS = new CachedArtifactId(Long.MAX_VALUE, ID_PLACEHOLDER,
        new AtomicInteger(), Long.MAX_VALUE);

    public long timestamp;
    public int id;
    public final AtomicInteger retryAttempts;
    public final long retryAfter;

    public CachedArtifactId(
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
