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
package io.aklivity.zilla.build.maven.plugins.zpm.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ZpmTemplate
{
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern DEPENDENCIES = Pattern.compile("\"dependencies\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private ZpmTemplate()
    {
    }

    static String substitute(
        String template,
        Map<String, String> properties)
    {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find())
        {
            String name = matcher.group(1);
            String value = properties.get(name);
            if (value == null)
            {
                throw new IllegalArgumentException(String.format("Undefined zpm template property: %s", name));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static List<String> dependencies(
        String config)
    {
        List<String> dependencies = new ArrayList<>();
        Matcher section = DEPENDENCIES.matcher(config);
        if (section.find())
        {
            Matcher quoted = QUOTED.matcher(section.group(1));
            while (quoted.find())
            {
                String[] coordinates = quoted.group(1).split(":");
                dependencies.add(String.format("%s:%s", coordinates[0], coordinates[1]));
            }
        }
        return dependencies;
    }
}
