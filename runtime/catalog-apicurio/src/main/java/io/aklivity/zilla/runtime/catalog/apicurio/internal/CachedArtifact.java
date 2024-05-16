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
package io.aklivity.zilla.runtime.catalog.apicurio.internal;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedArtifact
{
    public static final String SCHEMA_PLACEHOLDER = "schema";
    public static final CachedArtifact IN_PROGRESS = new CachedArtifact(SCHEMA_PLACEHOLDER);

    public final String artifact;
    public final AtomicInteger retryAttempts;

    public CachedArtifact(
        String artifact)
    {
        this.artifact = artifact;
        this.retryAttempts = new AtomicInteger();
    }

    public CachedArtifact(
        String artifact,
        AtomicInteger retryAttempts)
    {
        this.artifact = artifact;
        this.retryAttempts = retryAttempts;
    }
}
