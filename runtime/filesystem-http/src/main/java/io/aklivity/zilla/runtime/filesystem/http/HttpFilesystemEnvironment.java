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
package io.aklivity.zilla.runtime.filesystem.http;

public final class HttpFilesystemEnvironment
{
    private HttpFilesystemEnvironment()
    {

    }

    public static final String POLL_INTERVAL_PROPERTY_NAME = "zilla.filesystem.http.poll.interval";
    public static final String AUTHORIZATION_PROPERTY_NAME = "zilla.filesystem.http.authorization";
}
