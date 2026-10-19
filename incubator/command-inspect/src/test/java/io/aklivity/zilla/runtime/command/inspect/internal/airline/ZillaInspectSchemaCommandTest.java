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
package io.aklivity.zilla.runtime.command.inspect.internal.airline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.EngineSchemaReader;

public class ZillaInspectSchemaCommandTest
{
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream capturedOut;

    @Before
    public void captureStdout()
    {
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreStdout()
    {
        System.setOut(originalOut);
    }

    @Test
    public void shouldPrintSchemaToStdout() throws Exception
    {
        ZillaInspectSchemaCommand command = new ZillaInspectSchemaCommand();

        command.run();

        JsonObject printed = Json.createReader(new StringReader(capturedOut.toString(StandardCharsets.UTF_8))).readObject();

        EngineSchemaReader schemaReader = new EngineSchemaReader(new EngineInfo());
        JsonObject expected = schemaReader.stripIncubating(schemaReader.read());

        assertThat(printed, equalTo(expected));
    }

    @Test
    public void shouldWriteSchemaToOutputFileInsteadOfStdout() throws Exception
    {
        Path output = Files.createTempFile("zilla-inspect-schema", ".json");
        output.toFile().deleteOnExit();

        ZillaInspectSchemaCommand command = new ZillaInspectSchemaCommand();
        command.output = output.toString();

        command.run();

        assertThat(capturedOut.size(), equalTo(0));

        String written = Files.readString(output);
        assertThat(written.stripLeading(), startsWith("{"));

        JsonObject parsed = Json.createReader(new StringReader(written)).readObject();

        EngineSchemaReader schemaReader = new EngineSchemaReader(new EngineInfo());
        JsonObject expected = schemaReader.stripIncubating(schemaReader.read());

        assertThat(parsed, equalTo(expected));
    }

    @Test(expected = IOException.class)
    public void shouldRethrowWhenOutputPathInvalid() throws Exception
    {
        Path missingDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "zilla-inspect-missing-" + System.nanoTime());

        ZillaInspectSchemaCommand command = new ZillaInspectSchemaCommand();
        command.output = missingDirectory.resolve("schema.json").toString();

        command.run();
    }
}
