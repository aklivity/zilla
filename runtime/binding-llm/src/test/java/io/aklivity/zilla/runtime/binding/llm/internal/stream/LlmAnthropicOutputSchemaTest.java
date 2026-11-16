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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class LlmAnthropicOutputSchemaTest
{
    private static final String[] SCHEMAS =
    {
        "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"},\"b\":{\"type\":\"array\"," +
            "\"items\":{\"type\":\"integer\"}}}}",
        "{\"minimum\":1}",
        "{\"type\":\"integer\",\"minimum\":1}",
        "{\"type\":\"string\",\"description\":\"d\",\"minLength\":1}",
        "{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":2}",
        "{\"type\":\"object\",\"properties\":{\"field\":{\"type\":\"integer\",\"minimum\":1}}}",
        "{\"maximum\":1}",
        "{\"exclusiveMinimum\":1}",
        "{\"exclusiveMaximum\":1}",
        "{\"multipleOf\":2}",
        "{\"minLength\":1}",
        "{\"maxLength\":1}",
        "{\"maxItems\":1}",
        "{\"uniqueItems\":true}",
        "{\"contains\":{\"type\":\"string\"}}",
        "{\"minContains\":1}",
        "{\"maxContains\":1}",
        "{\"properties\":{\"a\":{\"minimum\":1}}}",
        "{\"properties\":{\"a\":{\"properties\":{\"b\":{\"maxLength\":3}}}}}",
        "{\"items\":{\"minLength\":1}}",
        "{\"items\":[{\"type\":\"string\"},{\"minLength\":1}]}",
        "{\"prefixItems\":[{\"type\":\"string\"},{\"maxItems\":1}]}",
        "{\"anyOf\":[{\"type\":\"string\"},{\"minimum\":1}]}",
        "{\"allOf\":[{\"oneOf\":[{\"maximum\":2}]}]}",
        "{\"$defs\":{\"x\":{\"minLength\":1}}}",
        "{\"definitions\":{\"x\":{\"maxItems\":1}}}",
        "{\"patternProperties\":{\"^a\":{\"minimum\":1}}}",
        "{\"dependentSchemas\":{\"a\":{\"maximum\":1}}}",
        "{\"additionalProperties\":{\"minLength\":1}}",
        "{\"not\":{\"minimum\":1}}",
        "{\"if\":{\"minimum\":1},\"then\":{\"type\":\"string\"}}",
        "{\"then\":{\"maxLength\":1}}",
        "{\"else\":{\"maxLength\":1}}",
        "{\"propertyNames\":{\"minLength\":1}}",
        "{\"unevaluatedProperties\":{\"minimum\":1}}",
        "{\"unevaluatedItems\":{\"minimum\":1}}",
        "{\"additionalItems\":{\"minimum\":1}}",
        "{\"contentSchema\":{\"minimum\":1}}",
        "{\"enum\":[{\"minimum\":1},{\"maxLength\":2}]}",
        "{\"const\":{\"minimum\":1}}",
        "{\"default\":{\"maximum\":3}}",
        "{\"examples\":[{\"minLength\":1}]}",
        "{\"properties\":{\"minimum\":{\"type\":\"number\"},\"maxLength\":{\"type\":\"string\"}}}",
        "{\"required\":[\"minimum\",\"maxLength\"]}",
        "{\"$ref\":\"#/$defs/x\"}",
        "{\"$ref\":\"#foo\"}",
        "{\"$ref\":\"#\"}",
        "{\"$ref\":\"http://example.com/x\"}",
        "{\"$ref\":\"\"}",
        "{\"$ref\":\"x\"}",
        "{\"$ref\":5}",
        "{\"properties\":{\"$ref\":{\"type\":\"string\"}}}",
        "{\"properties\":{\"a\":{\"$ref\":\"#\"}}}",
        "{\"items\":{\"$ref\":\"other.json\"}}",
        "{\"minItems\":0}",
        "{\"minItems\":1}",
        "{\"minItems\":2}",
        "{\"minItems\":\"2\"}",
        "{\"properties\":{\"a\":{\"minItems\":3}}}",
        "{\"items\":true}",
        "{\"additionalProperties\":false}",
        "{\"properties\":{\"a\":true,\"b\":false}}",
        "{\"description\":\"a minimum of 1\",\"title\":\"maxLength\"}",
        "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\",\"properties\":{\"b\":{\"type\":\"object\"," +
            "\"properties\":{\"c\":{\"type\":\"array\",\"items\":{\"anyOf\":[{\"type\":\"string\"},{\"maxLength\":2}]}}}}}}}}"
    };

    @Test
    public void shouldRejectTheSameKeywordAsTheScanner()
    {
        final LlmAnthropicOutputSchema output = new LlmAnthropicOutputSchema();

        for (String schema : SCHEMAS)
        {
            assertThat(schema, validated(output, schema), equalTo(scanned(schema)));
        }
    }

    private static String scanned(
        String schema)
    {
        final JsonParserEx parser = JsonEx.createParser();
        final byte[] bytes = schema.getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        final LlmSchemaScanner scanner = new LlmSchemaScanner(LlmSchemaScanner.TARGET_ANTHROPIC);
        scanner.begin();

        String keyword = null;
        while (keyword == null && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean text = event == JsonEvent.KEY_NAME ||
                event == JsonEvent.VALUE_STRING ||
                event == JsonEvent.VALUE_NUMBER;
            keyword = scanner.scan(event, text ? parser.getStringView() : null, false);
        }

        return keyword;
    }

    private static String validated(
        LlmAnthropicOutputSchema output,
        String schema)
    {
        final LlmAnthropicOutputSchema.Rejection rejection = new LlmAnthropicOutputSchema.Rejection();
        final JsonParserEx parser = output.newParser(JsonEx.createParser(), rejection);
        final byte[] bytes = schema.getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        String keyword = null;
        while (keyword == null && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean text = event == JsonEvent.KEY_NAME ||
                event == JsonEvent.VALUE_STRING ||
                event == JsonEvent.VALUE_NUMBER;

            if (rejection.rejected())
            {
                keyword = rejection.keyword(text ? parser.getStringView() : "");
            }
        }

        return keyword;
    }
}
