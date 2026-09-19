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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public class LlmDialectFactorySpiTest
{
    private final Map<String, LlmDialectFactorySpi> factoriesByName = ServiceLoader
        .load(LlmDialectFactorySpi.class)
        .stream()
        .map(Supplier::get)
        .collect(toMap(LlmDialectFactorySpi::name, identity()));

    @Test
    public void shouldResolveRegisteredDialect()
    {
        assertThat(factoriesByName.keySet(), hasItem("test"));
    }

    @Test
    public void shouldCreateMatchingDialect()
    {
        LlmDialect dialect = factoriesByName.get("test").create();

        assertThat(dialect, not(nullValue()));
        assertThat(dialect.name(), equalTo("test"));
    }

    @Test
    public void shouldDetectMatchingRequest()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        JsonEnvelope headers = headers(":method", "POST", ":path", "/v1/test");

        assertThat(dialect.detect(headers), is(true));
    }

    @Test
    public void shouldNotDetectUnrecognizedPath()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        JsonEnvelope headers = headers(":method", "POST", ":path", "/v1/other");

        assertThat(dialect.detect(headers), is(false));
    }

    @Test
    public void shouldNotDetectWrongMethod()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        JsonEnvelope headers = headers(":method", "GET", ":path", "/v1/test");

        assertThat(dialect.detect(headers), is(false));
    }

    @Test
    public void shouldNotDetectWithNoHeaders()
    {
        LlmDialect dialect = factoriesByName.get("test").create();

        assertThat(dialect.detect(JsonEnvelope.NONE), is(false));
    }

    @Test
    public void shouldExtractModelNameOnRequestDecode()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform decoder = dialect.supplyDecoder(LlmDialect.Kind.REQUEST, envelope);

        String output = transform(decoder, envelope, "{\"model\":\"gpt-4\"}");

        assertThat(output, equalTo("{\"model\":\"gpt-4\"}"));
        assertThat(decoder.identity(), is(true));
        DirectBufferEx extracted = envelope.get("model", 0);
        assertThat(extracted.getStringWithoutLengthUtf8(0, extracted.capacity()), equalTo("gpt-4"));
    }

    @Test
    public void shouldNotExtractUnrelatedFieldOnRequestDecode()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform decoder = dialect.supplyDecoder(LlmDialect.Kind.REQUEST, envelope);

        transform(decoder, envelope, "{\"other\":\"ignored\"}");

        assertThat(envelope.get("model", 0), nullValue());
    }

    @Test
    public void shouldForwardResponseDecodeUnchanged()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        TestJsonEnvelope envelope = new TestJsonEnvelope();

        JsonTransform decoder = dialect.supplyDecoder(LlmDialect.Kind.RESPONSE, envelope);

        assertThat(decoder.identity(), is(true));
        assertThat(transform(decoder, envelope, "{\"other\":\"value\"}"), equalTo("{\"other\":\"value\"}"));
    }

    @Test
    public void shouldSupplyIdentityEncoderForEachKind()
    {
        LlmDialect dialect = factoriesByName.get("test").create();
        TestJsonEnvelope envelope = new TestJsonEnvelope();

        for (LlmDialect.Kind kind : LlmDialect.Kind.values())
        {
            JsonTransform encoder = dialect.supplyEncoder(kind, envelope);

            assertThat(encoder.identity(), is(true));
        }
    }

    @Test
    public void shouldConvertKindValueOf()
    {
        assertThat(LlmDialect.Kind.valueOf("REQUEST"), equalTo(LlmDialect.Kind.REQUEST));
        assertThat(LlmDialect.Kind.valueOf("RESPONSE"), equalTo(LlmDialect.Kind.RESPONSE));
    }

    @Test
    public void shouldReturnKindValues()
    {
        assertThat(LlmDialect.Kind.values(), arrayContaining(LlmDialect.Kind.REQUEST, LlmDialect.Kind.RESPONSE));
    }

    private static String transform(
        JsonTransform transform,
        JsonEnvelope envelope,
        String json)
    {
        JsonParserEx parser = JsonEx.createParser();
        JsonGeneratorEx generator = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(parser).envelope(envelope).transform(transform).into(generator);

        byte[] bytes = json.getBytes(UTF_8);
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[8192]);
        JsonPipelineResult result = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0,
            output.capacity());

        assertThat(result.status(), equalTo(Status.COMPLETED));
        return output.getStringWithoutLengthUtf8(0, result.produced());
    }

    private static JsonEnvelope headers(
        String name1,
        String value1,
        String name2,
        String value2)
    {
        TestJsonEnvelope headers = new TestJsonEnvelope();
        headers.set(name1, buffer(value1));
        headers.set(name2, buffer(value2));
        return headers;
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
