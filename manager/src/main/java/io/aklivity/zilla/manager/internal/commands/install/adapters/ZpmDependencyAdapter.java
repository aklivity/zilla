/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal.commands.install.adapters;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.manager.internal.commands.install.ZpmDependency;

public final class ZpmDependencyAdapter implements JsonbAdapter<ZpmDependency, JsonString>
{
    private static final String DEFAULT_GROUP_ID = "io.aklivity.zilla";

    private static final String DEPENDENCY_FORMAT = "%s:%s:%s";
    private static final Pattern DEPENDENCY_PATTERN =
            Pattern.compile("(?<groupId>[^:]+):(?<artifactId>[^:]+)(?::(?<version>[^:]+))?");

    @Override
    public JsonString adaptToJson(
        ZpmDependency dependency)
    {
        String groupId = dependency.groupId;
        String artifactId = dependency.artifactId;
        String version = dependency.version;

        String value = String.format(DEPENDENCY_FORMAT, groupId, artifactId, version);

        return Json.createValue(value);
    }

    @Override
    public ZpmDependency adaptFromJson(
        JsonString value)
    {
        final String entry = ((JsonString) value).getString();
        final Matcher matcher = DEPENDENCY_PATTERN.matcher(entry);

        ZpmDependency dependency = null;
        if (matcher.matches())
        {
            dependency = new ZpmDependency();
            dependency.groupId = Optional.ofNullable(matcher.group("groupId")).orElse(DEFAULT_GROUP_ID);
            dependency.artifactId = matcher.group("artifactId");
            dependency.version = matcher.group("version");
        }

        return dependency;
    }
}
