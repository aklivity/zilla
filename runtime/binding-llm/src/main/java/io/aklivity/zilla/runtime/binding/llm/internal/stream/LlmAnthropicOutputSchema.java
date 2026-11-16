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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;

final class LlmAnthropicOutputSchema
{
    private static final String RESOURCE = "anthropic.output.schema.json";
    private static final String PROPERTY_NAMES = "propertyNames";

    private final JsonSchema schema;

    LlmAnthropicOutputSchema()
    {
        try (InputStream input = LlmAnthropicOutputSchema.class.getResourceAsStream(RESOURCE))
        {
            this.schema = JsonSchema.of(new String(input.readAllBytes(), UTF_8));
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    JsonParserEx newParser(
        JsonParserEx source,
        Rejection rejection)
    {
        return schema.newParser(false, source, rejection);
    }

    static final class Rejection implements Consumer<JsonSchemaDiagnostic>
    {
        private JsonSchemaDiagnostic diagnostic;

        @Override
        public void accept(
            JsonSchemaDiagnostic next)
        {
            if (diagnostic == null || PROPERTY_NAMES.equals(next.keyword()))
            {
                diagnostic = next;
            }
        }

        boolean rejected()
        {
            return diagnostic != null;
        }

        String keyword(
            CharSequence key)
        {
            final String pointer = diagnostic.pointer();

            return PROPERTY_NAMES.equals(diagnostic.keyword()) ? key.toString() : pointer.substring(pointer.lastIndexOf('/') + 1);
        }
    }
}
