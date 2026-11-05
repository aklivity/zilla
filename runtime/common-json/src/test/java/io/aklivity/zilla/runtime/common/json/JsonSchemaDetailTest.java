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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonLocation;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonSchemaDetailTest
{
    private static final String DECLARED =
        "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\"}},\"additionalProperties\":false}";

    private final List<String> log = new ArrayList<>();

    private boolean tapping = true;

    @Test
    void shouldReportUndeclaredMemberBeforeItsKeyIsForwarded()
    {
        String output = run(DECLARED, true, true, "{\"a\":1,\"x\":2}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME a",
            "fwd VALUE_NUMBER 1",
            "report additionalProperties |x|",
            "fwd KEY_NAME x",
            "fwd VALUE_NUMBER 2",
            "fwd END_OBJECT"), log.subList(0, 7));
        assertEquals("{\"a\":1,\"x\":2} ", output);
    }

    @Test
    void shouldReportUndeclaredMemberOfSlowPathObjectBeforeItsKeyIsForwarded()
    {
        String schema = "{\"properties\":{\"a\":{}},\"patternProperties\":{\"^p\":{}},\"additionalProperties\":false}";

        run(schema, true, true, "{\"p1\":1,\"x\":2}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME p1",
            "fwd VALUE_NUMBER 1",
            "report additionalProperties |x|",
            "fwd KEY_NAME x"), log.subList(0, 5));
    }

    @Test
    void shouldReportRequiredMemberBeforeEndObjectIsForwarded()
    {
        String schema = "{\"properties\":{\"a\":{},\"b\":{},\"c\":{}},\"required\":[\"a\",\"b\",\"c\"]}";

        run(schema, true, true, "{\"b\":1}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME b",
            "fwd VALUE_NUMBER 1",
            "report required |a|",
            "report required |c|",
            "fwd END_OBJECT"), log.subList(0, 6));
    }

    @Test
    void shouldReportNestedRequiredMemberWithContainerPointer()
    {
        String schema = "{\"properties\":{\"o\":{\"required\":[\"k\"]}}}";

        run(schema, true, true, "{\"o\":{}}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME o",
            "fwd START_OBJECT",
            "report required /o|k|",
            "fwd END_OBJECT"), log.subList(0, 5));
    }

    @Test
    void shouldInjectRequiredMemberInResponseToReport()
    {
        String schema = "{\"properties\":{\"a\":{},\"b\":{}},\"required\":[\"a\",\"b\"]}";

        String output = runInjecting(schema, "{\"a\":1}");

        assertEquals("{\"a\":1,\"b\":0} ", output);
    }

    @Test
    void shouldReportUnevaluatedMemberBeforeEndObjectIsForwarded()
    {
        String schema = "{\"$schema\":\"https://json-schema.org/draft/2019-09/schema\"," +
            "\"properties\":{\"a\":{}},\"unevaluatedProperties\":false}";

        run(schema, true, true, "{\"a\":1,\"x\":2}");

        assertEquals("report unevaluatedProperties |x|", log.get(log.size() - 2));
        assertEquals("fwd END_OBJECT", log.get(log.size() - 1));
    }

    @Test
    void shouldReportPropertyNameBeforeItsKeyIsForwarded()
    {
        String schema = "{\"propertyNames\":{\"maxLength\":2}}";

        run(schema, true, true, "{\"ab\":1,\"abc\":2}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME ab",
            "fwd VALUE_NUMBER 1",
            "report propertyNames |abc|",
            "fwd KEY_NAME abc"), log.subList(0, 5));
    }

    @Test
    void shouldReportConstValueBeforeItIsForwarded()
    {
        run("{\"properties\":{\"a\":{\"const\":\"yes\"}}}", true, true, "{\"a\":\"no\"}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME a",
            "report const /a||no",
            "fwd VALUE_STRING no"), log.subList(0, 4));
    }

    @Test
    void shouldReportEnumValueBeforeItIsForwarded()
    {
        run("{\"properties\":{\"a\":{\"enum\":[\"x\",\"y\"]}}}", true, true, "{\"a\":\"z\"}");

        assertEquals("report enum /a||z", log.get(2));
        assertEquals("fwd VALUE_STRING z", log.get(3));
    }

    @Test
    void shouldReportMinimumValueBeforeItIsForwarded()
    {
        run("{\"properties\":{\"a\":{\"minimum\":10}}}", true, true, "{\"a\":3}");

        assertEquals("report minimum /a||3", log.get(2));
        assertEquals("fwd VALUE_NUMBER 3", log.get(3));
    }

    @Test
    void shouldReportMaximumValueBeforeItIsForwarded()
    {
        run("{\"properties\":{\"a\":{\"maximum\":10}}}", true, true, "{\"a\":30}");

        assertEquals("report maximum /a||30", log.get(2));
        assertEquals("fwd VALUE_NUMBER 30", log.get(3));
    }

    @Test
    void shouldReportTypeValueBeforeItIsForwarded()
    {
        run("{\"properties\":{\"a\":{\"type\":\"integer\"}}}", true, true, "{\"a\":\"s\"}");

        assertEquals("report type /a||s", log.get(2));
        assertEquals("fwd VALUE_STRING s", log.get(3));
    }

    @Test
    void shouldReportOnceWhenKeyAndValueSplitAcrossWindows()
    {
        String schema = "{\"properties\":{\"a\":{\"type\":\"integer\"},\"name\":{\"const\":\"yes\"}}," +
            "\"additionalProperties\":false}";

        runSplit(schema, "{\"a\":1,\"unde", "clared\":2,\"name\":\"n", "o\"}");

        assertEquals(List.of(
            "fwd START_OBJECT",
            "fwd KEY_NAME a",
            "fwd VALUE_NUMBER 1",
            "report additionalProperties |undeclared|",
            "fwd KEY_NAME undeclared",
            "fwd VALUE_NUMBER 2",
            "fwd KEY_NAME name",
            "report const /name||no",
            "fwd VALUE_STRING no",
            "fwd END_OBJECT"), log);
    }

    @Test
    void shouldCompleteWithSameBytesAsPipelineWithoutDetail()
    {
        String document = "{\"a\": \"s\", \"x\": 2, \"y\" : [1, 2]}";
        tapping = false;

        String detailed = run(DECLARED, true, true, document);
        log.clear();
        String plain = run(DECLARED, true, false, document);

        assertEquals(plain, detailed);
        assertEquals(document + " ", plain);
    }

    @Test
    void shouldNotReportWithoutDetail()
    {
        String output = run(DECLARED, true, false, "{\"a\":\"s\",\"x\":2}");

        assertEquals(List.of(), log.stream().filter(l -> l.startsWith("report")).toList());
        assertEquals("{\"a\":\"s\",\"x\":2} ", output);
    }

    @Test
    void shouldNotReportValidDocument()
    {
        run(DECLARED, true, true, "{\"a\":1}");

        assertEquals(List.of(), log.stream().filter(l -> l.startsWith("report")).toList());
    }

    private String run(
        String schema,
        boolean lenient,
        boolean detailed,
        String document)
    {
        return runSplit(schema, lenient, detailed, document);
    }

    private String runSplit(
        String schema,
        String... windows)
    {
        return runSplit(schema, true, true, windows);
    }

    private String runSplit(
        String schema,
        boolean lenient,
        boolean detailed,
        String... windows)
    {
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[1024]);
        generator.wrap(output, 0, output.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(schema).validator(lenient))
            .transform(tapping ? new Tap() : new JsonTransform()
            {
                @Override
                public Status transform(
                    JsonController control,
                    JsonSource source,
                    JsonEvent event,
                    JsonSink sink)
                {
                    return sink.transform(control, source, event);
                }

                @Override
                public boolean deterministic()
                {
                    return true;
                }
            })
            .lenient(lenient)
            .detailed(detailed)
            .reporting(new Reporter())
            .into(JsonEx.createSink(generator));

        pipeline.reset();
        Status status = Status.STARVED;
        for (int i = 0; i < windows.length; i++)
        {
            boolean last = i == windows.length - 1;
            byte[] bytes = (windows[i] + (last ? " " : "")).getBytes(UTF_8);
            status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, last);
        }
        assertEquals(Status.COMPLETED, status);

        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private String runInjecting(
        String schema,
        String document)
    {
        List<String> missing = new ArrayList<>();
        JsonGeneratorEx generator = JsonEx.createGenerator();
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[1024]);
        generator.wrap(output, 0, output.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonSchema.of(schema).validator(true))
            .transform(new InjectMissing(missing))
            .lenient(true)
            .detailed(true)
            .reporting(new JsonReporter()
            {
                @Override
                public void rejected(
                    JsonDiagnostic diagnostic)
                {
                }

                @Override
                public void violated(
                    JsonViolation violation)
                {
                    missing.add(violation.member().toString());
                }
            })
            .into(JsonEx.createSink(generator));

        byte[] bytes = (document + " ").getBytes(UTF_8);
        pipeline.reset();
        assertEquals(Status.COMPLETED, pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length));

        byte[] out = new byte[generator.length()];
        output.getBytes(0, out);
        return new String(out, UTF_8);
    }

    private final class Reporter implements JsonReporter
    {
        @Override
        public void rejected(
            JsonDiagnostic diagnostic)
        {
        }

        @Override
        public void violated(
            JsonViolation violation)
        {
            JsonSource value = violation.value();
            log.add("report " + violation.keyword() + " " + violation.pointer() + "|" +
                (violation.member() != null ? violation.member() : "") + "|" +
                (value != null ? value.getStringView() : ""));
        }
    }

    private final class Tap implements JsonTransform
    {
        private final JsonController structured = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }
        };

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            boolean text = event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER;
            if (event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT)
            {
                log.add("fwd " + event + (text ? " " + source.getStringView() : ""));
            }
            return sink.transform(structured, source, event);
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    private static final class InjectMissing implements JsonTransform
    {
        private final List<String> missing;

        private JsonController upstream;
        private boolean downstreamVerbatim;
        private int depth;

        private final JsonController mediator = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void verbatim()
            {
                downstreamVerbatim = true;
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                upstream.consumed(sourceBytes);
            }
        };

        private final JsonController injected = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }
        };

        private InjectMissing(
            List<String> missing)
        {
            this.missing = missing;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstream = control;
            if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
            {
                depth++;
            }
            else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
            {
                if (event == JsonEvent.END_OBJECT && depth == 1)
                {
                    for (String name : missing)
                    {
                        sink.transform(injected, new TextSource(name), JsonEvent.KEY_NAME);
                        sink.transform(injected, new TextSource("0"), JsonEvent.VALUE_NUMBER);
                    }
                    missing.clear();
                }
                depth--;
            }
            Status status = sink.transform(mediator, source, forward(event));
            return depth == 0 && event == JsonEvent.END_OBJECT && status != Status.REJECTED ? Status.COMPLETED : status;
        }

        private JsonEvent forward(
            JsonEvent event)
        {
            boolean body = event != JsonEvent.START_DOCUMENT && event != JsonEvent.END_DOCUMENT && !event.segmented();
            return downstreamVerbatim && body ? JsonEvent.VERBATIM : event;
        }

        @Override
        public void reset()
        {
            downstreamVerbatim = false;
            depth = 0;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }

    private static final class TextSource implements JsonSource
    {
        private final CharSequence text;

        private TextSource(
            CharSequence text)
        {
            this.text = text;
        }

        @Override
        public CharSequence getStringView()
        {
            return text;
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }

        @Override
        public String getString()
        {
            return text.toString();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }
    }
}
