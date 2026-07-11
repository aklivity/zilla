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
package io.aklivity.zilla.runtime.command.logs.internal.printer;

import java.io.PrintStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LogsPrinter
{
    private static final String FORMAT = "%s [%s] [%016x] %s %s%n";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private final List<LogRecord> records;
    private final String grep;

    public LogsPrinter(
        List<LogRecord> records,
        String grep)
    {
        this.records = records;
        this.grep = grep;
    }

    public boolean print(
        PrintStream out,
        String waitFor)
    {
        boolean matched = false;
        for (LogRecord record : records)
        {
            if (grep == null || record.eventName().contains(grep))
            {
                out.format(FORMAT, record.qualifiedName(), asDateTime(record.timestamp()), record.traceId(),
                    record.eventName(), record.message());
            }

            if (waitFor != null && record.eventName().equals(waitFor))
            {
                matched = true;
            }
        }

        return matched;
    }

    private static String asDateTime(
        long timestamp)
    {
        Instant instant = Instant.ofEpochMilli(timestamp);
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        return offsetDateTime.format(FORMATTER);
    }
}
