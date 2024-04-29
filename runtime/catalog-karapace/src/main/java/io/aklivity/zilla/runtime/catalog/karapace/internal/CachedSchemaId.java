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
package io.aklivity.zilla.runtime.catalog.karapace.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

public class CachedSchemaId
{
    public static final CachedSchemaId IN_PROGRESS = new CachedSchemaId(Long.MAX_VALUE, NO_SCHEMA_ID);

    public long timestamp;
    public int id;

    public CachedSchemaId(
        long timestamp,
        int id)
    {
        this.timestamp = timestamp;
        this.id = id;
    }

    public boolean expired(
        long maxAgeMillis)
    {
        return System.currentTimeMillis() - this.timestamp > maxAgeMillis;
    }
}
