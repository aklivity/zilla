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
package io.aklivity.zilla.runtime.filesystem.http.internal;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class HttpFileSystemConfiguration
{
    public static final String POLL_INTERVAL_PROPERTY_NAME = "zilla.filesystem.http.poll.interval";
    public static final String CONFIG_AUTHORIZATION_PROPERTY_NAME = "zilla.filesystem.http.config.authorization";

    private static final Duration POLL_INTERVAL_PROPERTY_DEFAULT = Duration.parse("PT30S");

    private final Map<String, ?> env;

    HttpFileSystemConfiguration(
        Map<String, ?> env)
    {
        this.env = Objects.requireNonNull(env);
    }

    public Duration pollInterval()
    {
        String value = env != null ? (String) env.get(POLL_INTERVAL_PROPERTY_NAME) : null;
        return value != null ? Duration.parse(value) : POLL_INTERVAL_PROPERTY_DEFAULT;
    }

    public String authorization()
    {
        return (String) env.get(CONFIG_AUTHORIZATION_PROPERTY_NAME);
    }
}
