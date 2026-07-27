/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.http;

public enum HttpVersion
{
    HTTP_1_1("http/1.1"),
    HTTP_2("h2");

    private final String name;

    HttpVersion(
        String name)
    {
        this.name = name;
    }

    public String asString()
    {
        return name;
    }

    public static HttpVersion of(
        String name)
    {
        switch (name)
        {
        case "http/1.1":
            return HTTP_1_1;
        case "h2":
            return HTTP_2;
        default:
            return null;
        }
    }
}
