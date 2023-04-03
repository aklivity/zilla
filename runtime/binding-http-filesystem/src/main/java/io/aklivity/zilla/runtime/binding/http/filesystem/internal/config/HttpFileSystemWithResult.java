/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.config;

import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String16FW;

public class HttpFileSystemWithResult
{
    private final String16FW path;
    private final int capabilities;
    private final String16FW tag;
    private final long timeout;

    HttpFileSystemWithResult(
        String16FW path,
        int capabilities,
        String16FW tag,
        long timeout)
    {
        this.path = path;
        this.capabilities = capabilities;
        this.tag = tag;
        this.timeout = timeout;
    }

    public String16FW path()
    {
        return path;
    }

    public int capabilities()
    {
        return capabilities;
    }

    public String16FW tag()
    {
        return tag;
    }

    public long timeout()
    {
        return timeout;
    }

}
