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
package io.aklivity.zilla.runtime.model.core.config;

public enum StringPattern
{
    EMAIL("email", "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}"),
    DATE("date", "^(\\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])"),
    DATE_TIME("date-time", "^(\\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)Z");

    public final String format;
    public final String pattern;

    StringPattern(
        String format,
        String pattern)
    {
        this.format = format;
        this.pattern = pattern;
    }


    public static String of(
        String format)
    {
        switch (format)
        {
        case "email":
            return EMAIL.pattern;
        case "date":
            return DATE.pattern;
        case "date-time":
            return DATE_TIME.pattern;
        default:
            return null;
        }
    }
}
