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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class LlmSchemaScannerTest
{
    private final LlmSchemaScanner anthropic = new LlmSchemaScanner(LlmSchemaScanner.TARGET_ANTHROPIC);
    private final LlmSchemaScanner gemini = new LlmSchemaScanner(LlmSchemaScanner.TARGET_GEMINI);

    @Test
    public void shouldAcceptSchemaWithoutDeniedKeywords()
    {
        assertThat(scan(anthropic, "{'type':'object','properties':{'a':{'type':'string','enum':['x','y']}}," +
            "'required':['a'],'additionalProperties':false}"), nullValue());
    }

    @Test
    public void shouldRejectNumericConstraintsForAnthropic()
    {
        for (String keyword : new String[] {"minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf"})
        {
            assertThat(keyword, scan(anthropic, "{'type':'integer','" + keyword + "':1}"), equalTo(keyword));
        }
    }

    @Test
    public void shouldRejectStringConstraintsForAnthropic()
    {
        assertThat(scan(anthropic, "{'type':'string','minLength':1}"), equalTo("minLength"));
        assertThat(scan(anthropic, "{'type':'string','maxLength':1}"), equalTo("maxLength"));
    }

    @Test
    public void shouldRejectArrayConstraintsForAnthropic()
    {
        for (String keyword : new String[] {"maxItems", "uniqueItems", "contains", "minContains", "maxContains"})
        {
            assertThat(keyword, scan(anthropic, "{'type':'array','" + keyword + "':1}"), equalTo(keyword));
        }
    }

    @Test
    public void shouldAcceptMinItemsZeroAndOneForAnthropic()
    {
        assertThat(scan(anthropic, "{'type':'array','minItems':0}"), nullValue());
        assertThat(scan(anthropic, "{'type':'array','minItems':1}"), nullValue());
    }

    @Test
    public void shouldRejectMinItemsAboveOneForAnthropic()
    {
        assertThat(scan(anthropic, "{'type':'array','minItems':2}"), equalTo("minItems"));
    }

    @Test
    public void shouldAcceptMinItemsAboveOneForGemini()
    {
        assertThat(scan(gemini, "{'type':'array','minItems':5}"), nullValue());
    }

    @Test
    public void shouldRejectExternalReference()
    {
        assertThat(scan(anthropic, "{'$ref':'https://example.com/a.json'}"), equalTo("$ref"));
        assertThat(scan(gemini, "{'$ref':'https://example.com/a.json'}"), equalTo("$ref"));
        assertThat(scan(gemini, "{'$ref':''}"), equalTo("$ref"));
    }

    @Test
    public void shouldAcceptInternalReference()
    {
        assertThat(scan(anthropic, "{'$defs':{'a':{'type':'string'}},'properties':{'b':{'$ref':'#/$defs/a'}}}"), nullValue());
        assertThat(scan(gemini, "{'$defs':{'a':{'type':'string'}},'properties':{'b':{'$ref':'#/$defs/a'}}}"), nullValue());
    }

    @Test
    public void shouldRejectRootRecursionForAnthropicOnly()
    {
        assertThat(scan(anthropic, "{'properties':{'child':{'$ref':'#'}}}"), equalTo("$ref"));
        assertThat(scan(gemini, "{'properties':{'child':{'$ref':'#'}}}"), nullValue());
    }

    @Test
    public void shouldRejectDeniedKeywordsForGemini()
    {
        for (String keyword : new String[] {"patternProperties", "not", "if", "then", "else", "dependentSchemas",
            "unevaluatedProperties"})
        {
            assertThat(keyword, scan(gemini, "{'type':'object','" + keyword + "':{}}"), equalTo(keyword));
        }
    }

    @Test
    public void shouldAcceptNumericConstraintsForGemini()
    {
        assertThat(scan(gemini, "{'type':'integer','minimum':1,'maximum':5,'minLength':1,'maxItems':3}"), nullValue());
    }

    @Test
    public void shouldNotTreatPropertyNamesAsKeywords()
    {
        assertThat(scan(anthropic, "{'type':'object','properties':{'minimum':{'type':'integer'},'maxItems':{'type':'string'}," +
            "'$ref':{'type':'string'}}}"), nullValue());
        assertThat(scan(gemini, "{'type':'object','properties':{'not':{'type':'string'},'if':{'type':'string'}}}"),
            nullValue());
    }

    @Test
    public void shouldNotTreatDataAsKeywords()
    {
        assertThat(scan(anthropic, "{'enum':[{'minimum':1},'maxItems'],'const':{'maxLength':3},'default':{'$ref':'x'}," +
            "'examples':[{'minLength':1}]}"), nullValue());
    }

    @Test
    public void shouldScanNestedSchemas()
    {
        assertThat(scan(anthropic, "{'properties':{'a':{'items':{'anyOf':[{'type':'string'},{'minimum':1}]}}}}"),
            equalTo("minimum"));
        assertThat(scan(anthropic, "{'$defs':{'a':{'maxLength':1}}}"), equalTo("maxLength"));
        assertThat(scan(anthropic, "{'items':[{'type':'string'},{'maxItems':1}]}"), equalTo("maxItems"));
        assertThat(scan(gemini, "{'properties':{'a':{'not':{}}}}"), equalTo("not"));
    }

    @Test
    public void shouldScanBooleanSchemas()
    {
        assertThat(scan(anthropic, "{'additionalProperties':false,'items':true}"), nullValue());
    }

    @Test
    public void shouldScanDeeplyNestedSchema()
    {
        final String schema = "{'items':".repeat(60) + "{'maxLength':1}" + "}".repeat(60);

        assertThat(scan(anthropic, schema), equalTo("maxLength"));
    }

    @Test
    public void shouldBeginAgain()
    {
        assertThat(scan(anthropic, "{'minimum':1}"), equalTo("minimum"));
        assertThat(scan(anthropic, "{'type':'string'}"), nullValue());
    }

    private static String scan(
        LlmSchemaScanner scanner,
        String text)
    {
        final byte[] bytes = json(text).getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();

        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        scanner.begin();

        String rejected = null;

        while (rejected == null && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean carries = event == JsonEvent.KEY_NAME ||
                event == JsonEvent.VALUE_STRING ||
                event == JsonEvent.VALUE_NUMBER;

            rejected = scanner.scan(event, carries ? parser.getStringView() : null, carries && parser.deferredBytes());
        }

        return rejected;
    }
}
