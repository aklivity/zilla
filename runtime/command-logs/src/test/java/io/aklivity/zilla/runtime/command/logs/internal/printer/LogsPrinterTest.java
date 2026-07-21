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
package io.aklivity.zilla.runtime.command.logs.internal.printer;

import static org.hamcrest.Matchers.equalTo;
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
    public void shouldPrintTextFormatByDefault() throws Exception
    {
        // GIVEN
        LogRecord started = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Engine Started.");
        LogRecord stopped = new LogRecord(2000L, 0x2aL, "engine:events", "engine.stopped", "Engine Stopped.");
        String expectedOutput =
            String.format("engine:events [%s] [000000000000002a] engine.started Engine Started.%n", asDateTime(1000L)) +
            String.format("engine:events [%s] [000000000000002a] engine.stopped Engine Stopped.%n", asDateTime(2000L));

        LogsPrinter printer = new LogsPrinter(List.of(started, stopped), false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldPrintJsonFormatWhenRequested() throws Exception
    {
        // GIVEN
        LogRecord started = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Engine Started.");
        String expectedOutput = "{\"namespace\":\"engine:events\",\"timestamp\":1000,\"traceId\":\"000000000000002a\"," +
            "\"event\":\"engine.started\",\"message\":\"Engine Started.\"}" + System.lineSeparator();

        LogsPrinter printer = new LogsPrinter(List.of(started), true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldEscapeSpecialCharactersInJsonMessage() throws Exception
    {
        // GIVEN
        LogRecord withQuotes = new LogRecord(1000L, 0x2aL, "engine:events", "engine.started", "Said \"hello\".");
        String expectedOutput = "{\"namespace\":\"engine:events\",\"timestamp\":1000,\"traceId\":\"000000000000002a\"," +
            "\"event\":\"engine.started\",\"message\":\"Said \\\"hello\\\".\"}" + System.lineSeparator();

        LogsPrinter printer = new LogsPrinter(List.of(withQuotes), true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(expectedOutput));
    }

    @Test
    public void shouldPrintNothingWhenEmptyAndText() throws Exception
    {
        // GIVEN
        LogsPrinter printer = new LogsPrinter(List.of(), false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(""));
    }

    @Test
    public void shouldPrintNothingWhenEmptyAndJson() throws Exception
    {
        // GIVEN
        LogsPrinter printer = new LogsPrinter(List.of(), true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);

        // WHEN
        printer.print(out);

        // THEN
        assertThat(os.toString("UTF8"), equalTo(""));
    }

    private static String asDateTime(
        long timestamp)
    {
        Instant instant = Instant.ofEpochMilli(timestamp);
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        return offsetDateTime.format(FORMATTER);
    }
}
