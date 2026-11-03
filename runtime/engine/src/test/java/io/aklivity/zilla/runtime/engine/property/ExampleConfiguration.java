/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.property;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.Configuration;

public final class ExampleConfiguration extends Configuration
{
    public static final String EXAMPLE_ENTRIES_NAME = "zilla.binding.example.entries";
    public static final String EXAMPLE_CREATOR_ENTRIES_NAME = "zilla.binding.example.creator.entries";
    public static final String EXAMPLE_LIMIT_NAME = "zilla.binding.example.limit";

    public static final JsonPropertyDef<ExampleEntry[]> EXAMPLE_ENTRIES;
    public static final JsonPropertyDef<ExampleCreatorEntry[]> EXAMPLE_CREATOR_ENTRIES;
    public static final IntPropertyDef EXAMPLE_LIMIT;

    private static final ConfigurationDef EXAMPLE_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.example");
        EXAMPLE_ENTRIES = config.property(
            ExampleEntry[].class,
            "entries",
            JsonSchema.of(ExampleConfiguration.class, "example-entries.schema.json"));
        EXAMPLE_CREATOR_ENTRIES = config.property(
            ExampleCreatorEntry[].class,
            "creator.entries",
            JsonSchema.of(ExampleConfiguration.class, "example-entries.schema.json"));
        EXAMPLE_LIMIT = config.property("limit", 1);
        EXAMPLE_CONFIG = config;
    }

    public ExampleConfiguration(
        Configuration config)
    {
        super(EXAMPLE_CONFIG, config);
    }

    public ExampleEntry[] entries()
    {
        return EXAMPLE_ENTRIES.get(this);
    }

    public ExampleCreatorEntry[] creatorEntries()
    {
        return EXAMPLE_CREATOR_ENTRIES.get(this);
    }

    public int limit()
    {
        return EXAMPLE_LIMIT.getAsInt(this);
    }
}
