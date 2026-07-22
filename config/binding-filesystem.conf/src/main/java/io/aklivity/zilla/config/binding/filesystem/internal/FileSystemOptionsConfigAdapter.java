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
package io.aklivity.zilla.config.binding.filesystem.internal;

import static io.aklivity.zilla.config.binding.filesystem.FileSystemSymbolicLinksConfig.IGNORE;

import java.net.URI;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.filesystem.FileSystemOptionsConfig;
import io.aklivity.zilla.config.binding.filesystem.FileSystemSymbolicLinksConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class FileSystemOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String LOCATION_NAME = "location";
    private static final String SYMLINKS_NAME = "symlinks";

    private static final FileSystemSymbolicLinksConfig SYMLINKS_DEFAULT = IGNORE;

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        FileSystemOptionsConfig fsOptions = (FileSystemOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (fsOptions.location != null)
        {
            object.add(LOCATION_NAME, fsOptions.location.toString());
        }

        if (fsOptions.symlinks != SYMLINKS_DEFAULT)
        {
            object.add(SYMLINKS_NAME, fsOptions.symlinks.toString().toLowerCase());
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        URI location = object.containsKey(LOCATION_NAME)
                ? URI.create(object.getString(LOCATION_NAME))
                : null;

        FileSystemSymbolicLinksConfig symlinks = object.containsKey(SYMLINKS_NAME)
                ? FileSystemSymbolicLinksConfig.valueOf(object.getString(SYMLINKS_NAME).toUpperCase())
                : SYMLINKS_DEFAULT;

        return new FileSystemOptionsConfig(location, symlinks);
    }
}
