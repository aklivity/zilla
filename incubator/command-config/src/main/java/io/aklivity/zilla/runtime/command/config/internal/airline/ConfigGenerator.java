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
package io.aklivity.zilla.runtime.command.config.internal.airline;

import java.util.List;
import java.util.regex.Pattern;

public interface ConfigGenerator
{
    String generate();

    default String unquoteEnvVars(
        String yaml,
        List<String> unquotedEnvVars)
    {
        for (String envVar : unquotedEnvVars)
        {
            yaml = yaml.replaceAll(
                Pattern.quote(String.format("\"${{env.%s}}\"", envVar)),
                String.format("\\${{env.%s}}", envVar)
            );
        }
        return yaml;
    }
}
