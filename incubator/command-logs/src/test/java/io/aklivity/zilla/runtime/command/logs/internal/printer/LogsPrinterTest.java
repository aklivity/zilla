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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.Test;

public class LogsPrinterTest
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    @Test
    public void shouldPrintAllRecordsWhenNoGrep() throws Exception
    {
        // GIVEN
        LogRecord started = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Engine Started.");
        LogRecord stopped = new LogRecord(2000L, 0x2aL, "engine:events", "engine.stopped", "Engine Stopped.");
        String expectedOutput =
            String.format("engine:events [%s] [000000000000002a] engine.started Engine Started.%n", asDateTime(1000L)) +
            String.format("engine:events [%s] [000000000000002a] engine.stopped Engine Stopped.%n", asDateTime(2000L));

        LogsPrinter printer = new LogsPrinter(List.of(started, stopped), null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        boolean matched = printer.print(out, null);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
        assertThat(matched, is(false));
    }

    @Test
    public void shouldFilterRecordsByGrep() throws Exception
    {
        // GIVEN
        LogRecord started = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Engine Started.");
        LogRecord stopped = new LogRecord(2000L, 0x2aL, "engine:events", "engine.stopped", "Engine Stopped.");
        String expectedOutput =
            String.format("engine:events [%s] [000000000000002a] engine.started Engine Started.%n", asDateTime(1000L));

        LogsPrinter printer = new LogsPrinter(List.of(started, stopped), "started");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        boolean matched = printer.print(out, null);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
        assertThat(matched, is(false));
    }

    @Test
    public void shouldMatchWaitForEvent() throws Exception
    {
        // GIVEN
        LogRecord started = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Engine Started.");
        LogsPrinter printer = new LogsPrinter(List.of(started), null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        boolean matched = printer.print(out, "engine.started");

        // THEN
        assertThat(matched, is(true));
    }

    @Test
    public void shouldNotMatchWaitForEventWhenAbsent() throws Exception
    {
        // GIVEN
        LogRecord started = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Engine Started.");
        LogsPrinter printer = new LogsPrinter(List.of(started), null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        boolean matched = printer.print(out, "engine.stopped");

        // THEN
        assertThat(matched, is(false));
    }

    @Test
    public void shouldPrintNothingWhenEmpty() throws Exception
    {
        // GIVEN
        LogsPrinter printer = new LogsPrinter(List.of(), null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        boolean matched = printer.print(out, "engine.started");

        // THEN
        assertThat(os.toString("UTF8"), equalTo(""));
        assertThat(matched, is(false));
    }

    private static String asDateTime(
        long timestamp)
    {
        Instant instant = Instant.ofEpochMilli(timestamp);
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        return offsetDateTime.format(FORMATTER);
    }
}
