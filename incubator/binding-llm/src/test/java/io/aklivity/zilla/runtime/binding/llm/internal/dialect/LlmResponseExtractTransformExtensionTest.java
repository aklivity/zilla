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
package io.aklivity.zilla.runtime.binding.llm.internal.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmResponseExtractTransform;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonSource;

public class LlmResponseExtractTransformExtensionTest
{
    @Test
    public void shouldExtractEveryUsageFieldFromSubclassOutsideDialectPackage()
    {
        Envelope envelope = new Envelope();
        ResponseExtractTransform transform = new ResponseExtractTransform(envelope);

        String text = transform(transform, envelope,
            "{\"meter\":{\"in\":1,\"cacheWrite\":2,\"cacheRead\":3,\"out\":4,\"reasoning\":5,\"total\":15}}");

        assertThat(text, equalTo(
            "{\"meter\":{\"in\":1,\"cacheWrite\":2,\"cacheRead\":3,\"out\":4,\"reasoning\":5,\"total\":15}}"));
        assertThat(intValue(envelope, "usage.inputTokens"), equalTo(1));
        assertThat(intValue(envelope, "usage.cacheWriteTokens"), equalTo(2));
        assertThat(intValue(envelope, "usage.cacheReadTokens"), equalTo(3));
        assertThat(intValue(envelope, "usage.outputTokens"), equalTo(4));
        assertThat(intValue(envelope, "usage.reasoningTokens"), equalTo(5));
        assertThat(intValue(envelope, "usage.totalTokens"), equalTo(15));
    }

    @Test
    public void shouldExtractEveryErrorFieldFromSubclassOutsideDialectPackage()
    {
        Envelope envelope = new Envelope();
        ResponseExtractTransform transform = new ResponseExtractTransform(envelope);

        String text = transform(transform, envelope,
            "{\"fault\":{\"code\":503,\"kind\":\"overloaded\",\"text\":\"try later\"}}");

        assertThat(text, equalTo("{\"fault\":{\"code\":503,\"kind\":\"overloaded\",\"text\":\"try later\"}}"));
        assertThat(intValue(envelope, "error.status"), equalTo(503));
        assertThat(stringValue(envelope, "error.type"), equalTo("overloaded"));
        assertThat(stringValue(envelope, "error.message"), equalTo("try later"));
    }

    @Test
    public void shouldExtractErrorFromEventName()
    {
        Envelope envelope = new Envelope();
        ResponseExtractTransform transform = new ResponseExtractTransform(envelope);

        transform.event("failure");
        transform(transform, envelope, "{\"text\":\"stream broke\"}");

        assertThat(stringValue(envelope, "error.type"), equalTo("failure"));
        assertThat(stringValue(envelope, "error.message"), equalTo("stream broke"));
    }

    @Test
    public void shouldNotExtractErrorForOrdinaryEvent()
    {
        Envelope envelope = new Envelope();
        ResponseExtractTransform transform = new ResponseExtractTransform(envelope);

        transform.event("delta");
        transform(transform, envelope, "{\"text\":\"hello\"}");

        assertThat(envelope.get("error.type", 0), nullValue());
        assertThat(envelope.get("error.message", 0), nullValue());
        assertThat(envelope.get("error.status", 0), nullValue());
    }

    @Test
    public void shouldNotExtractUnrecognizedFields()
    {
        Envelope envelope = new Envelope();
        ResponseExtractTransform transform = new ResponseExtractTransform(envelope);

        transform(transform, envelope, "{\"other\":{\"in\":1}}");

        assertThat(envelope.get("usage.inputTokens", 0), nullValue());
    }

    @Test
    public void shouldBeIdentity()
    {
        assertThat(new ResponseExtractTransform(new Envelope()).identity(), is(true));
    }

    private static int intValue(
        JsonEnvelope envelope,
        String name)
    {
        DirectBufferEx value = envelope.get(name, 0);
        return Integer.parseInt(value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    private static String stringValue(
        JsonEnvelope envelope,
        String name)
    {
        DirectBufferEx value = envelope.get(name, 0);
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    private static String transform(
        ResponseExtractTransform transform,
        JsonEnvelope envelope,
        String json)
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .envelope(envelope)
            .transform(transform)
            .into(JsonEx.createGenerator());

        byte[] bytes = json.getBytes(UTF_8);
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[1024]);
        JsonPipelineResult result = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0,
            output.capacity());

        assertThat(result.status(), equalTo(Status.COMPLETED));

        return output.getStringWithoutLengthUtf8(0, result.produced());
    }

    private static final class ResponseExtractTransform extends LlmResponseExtractTransform
    {
        private String eventName;

        private ResponseExtractTransform(
            JsonEnvelope envelope)
        {
            super(envelope);
        }

        @Override
        public void event(
            String name)
        {
            this.eventName = name;
        }

        @Override
        protected void onField(
            String fieldPath,
            JsonSource source,
            JsonEvent event)
        {
            switch (fieldPath)
            {
            case "meter.in":
                inputTokens(source.getInt());
                break;
            case "meter.cacheWrite":
                cacheWriteTokens(source.getInt());
                break;
            case "meter.cacheRead":
                cacheReadTokens(source.getInt());
                break;
            case "meter.out":
                outputTokens(source.getInt());
                break;
            case "meter.reasoning":
                reasoningTokens(source.getInt());
                break;
            case "meter.total":
                totalTokens(source.getInt());
                break;
            case "fault.code":
                errorStatus(source.getInt());
                break;
            case "fault.kind":
                errorType(source.getString());
                break;
            case "fault.text":
                errorMessage(source.getString());
                break;
            case "text":
                if ("failure".equals(eventName))
                {
                    errorType(eventName);
                    errorMessage(source.getString());
                }
                break;
            default:
                break;
            }
        }
    }

    private static final class Envelope implements JsonEnvelope
    {
        private final Map<String, DirectBufferEx> valuesByName = new HashMap<>();

        @Override
        public int count(
            String name)
        {
            return valuesByName.containsKey(name) ? 1 : 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            return index == 0 ? valuesByName.get(name) : null;
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
            valuesByName.put(name, value);
        }
    }
}
