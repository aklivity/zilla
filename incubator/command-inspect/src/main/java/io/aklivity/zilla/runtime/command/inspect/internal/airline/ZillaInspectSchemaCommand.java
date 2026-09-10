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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import jakarta.json.JsonObject;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.EngineSchemaReader;
import io.aklivity.zilla.runtime.command.ZillaCommand;

@Command(name = "schema", description = "Print the merged zilla.yaml JSON Schema")
public final class ZillaInspectSchemaCommand extends ZillaCommand
{
    @Option(name = {"-o", "--output"},
        description = "Write the schema to this file instead of stdout")
    public String output;

    @Override
    public void run()
    {
        try
        {
            EngineSchemaReader schemaReader = new EngineSchemaReader(new EngineInfo());
            JsonObject schema = schemaReader.stripIncubating(schemaReader.read());

            String text = EngineSchemaReader.write(schema);

            if (output != null)
            {
                Files.writeString(Paths.get(output), text);
            }
            else
            {
                System.out.println(text);
            }
        }
        catch (IOException ex)
        {
            rethrowUnchecked(ex);
        }
    }
}
