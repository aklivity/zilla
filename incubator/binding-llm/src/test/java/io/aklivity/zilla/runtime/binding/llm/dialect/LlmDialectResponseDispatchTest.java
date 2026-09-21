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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import org.agrona.DirectBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmTestDialect.LlmTestResponseDecodeTransform;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmTestDialect.LlmTestResponseEncodeSink;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmAnthropicDecodeTransform;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmAnthropicEncodeSink;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmOpenaiDecodeTransform;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmOpenaiEncodeSink;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Regression coverage for the bug this dispatch replaces: {@code LlmClientFactory} used to resolve a
 * cross-dialect response decode/encode pair via a hardcoded {@code "openai".equals(dialectName) ? ... :
 * anthropic} switch, so any registered dialect whose name was not literally {@code "openai"} -- including
 * a third dialect contributed from outside this module, exactly as {@link LlmDialect}'s own javadoc
 * advertises -- silently fell through to Anthropic's response transform instead of its own. Now that
 * {@code LlmClientFactory} calls {@code client.target.supplyResponseDecodeTransform()}/
 * {@code client.source.supplyResponseEncodeSink(...)} directly on the resolved {@link LlmDialect}, dispatch
 * is inherent to the interface call, not a name lookup -- these tests exercise the same call shape
 * {@code LlmClientFactory} uses and confirm a third dialect never receives Anthropic's (or OpenAI's) own
 * response mapping.
 */
public class LlmDialectResponseDispatchTest
{
    @Test
    public void shouldDispatchOpenaiDialectToItsOwnResponseTransforms()
    {
        LlmDialect dialect = new LlmOpenaiDialect();

        JsonTransform decode = dialect.supplyResponseDecodeTransform();
        JsonSink encode = dialect.supplyResponseEncodeSink(JsonEnvelope.NONE, LlmDialectResponseDispatchTest::discard);

        assertThat(decode, instanceOf(LlmOpenaiDecodeTransform.class));
        assertThat(decode, not(instanceOf(LlmAnthropicDecodeTransform.class)));
        assertThat(encode, instanceOf(LlmOpenaiEncodeSink.class));
        assertThat(encode, not(instanceOf(LlmAnthropicEncodeSink.class)));
        assertThat("LlmClientFactory casts every dialect's result to these, unconditionally",
            decode, instanceOf(LlmDialectEvent.class));
        assertThat("LlmClientFactory casts every dialect's result to these, unconditionally",
            encode, instanceOf(LlmDialectTerminator.class));
    }

    @Test
    public void shouldDispatchAnthropicDialectToItsOwnResponseTransforms()
    {
        LlmDialect dialect = new LlmAnthropicDialect();

        JsonTransform decode = dialect.supplyResponseDecodeTransform();
        JsonSink encode = dialect.supplyResponseEncodeSink(JsonEnvelope.NONE, LlmDialectResponseDispatchTest::discard);

        assertThat(decode, instanceOf(LlmAnthropicDecodeTransform.class));
        assertThat(decode, not(instanceOf(LlmOpenaiDecodeTransform.class)));
        assertThat(encode, instanceOf(LlmAnthropicEncodeSink.class));
        assertThat(encode, not(instanceOf(LlmOpenaiEncodeSink.class)));
        assertThat(decode, instanceOf(LlmDialectEvent.class));
        assertThat(encode, instanceOf(LlmDialectTerminator.class));
    }

    @Test
    public void shouldDispatchThirdRegisteredDialectToItsOwnResponseTransformsRatherThanFallingThroughToAnthropic()
    {
        LlmDialect dialect = new LlmTestDialect();

        JsonTransform decode = dialect.supplyResponseDecodeTransform();
        JsonSink encode = dialect.supplyResponseEncodeSink(JsonEnvelope.NONE, LlmDialectResponseDispatchTest::discard);

        assertThat(decode, instanceOf(LlmTestResponseDecodeTransform.class));
        assertThat(decode, not(instanceOf(LlmAnthropicDecodeTransform.class)));
        assertThat(decode, not(instanceOf(LlmOpenaiDecodeTransform.class)));
        assertThat(encode, instanceOf(LlmTestResponseEncodeSink.class));
        assertThat(encode, not(instanceOf(LlmAnthropicEncodeSink.class)));
        assertThat(encode, not(instanceOf(LlmOpenaiEncodeSink.class)));
    }

    @Test
    public void shouldSatisfyTheCastEveryDialectsResponseTransformsMustSupport()
    {
        // LlmClientFactory unconditionally casts every dialect's supplyResponseDecodeTransform()/
        // supplyResponseEncodeSink(...) result to these two interfaces -- a third-party dialect that
        // forgets to implement them (as a no-op, if it has no real use for either) would compile fine
        // here but throw ClassCastException the moment a live engine actually builds a cross-dialect
        // response pipeline for it.
        LlmDialect dialect = new LlmTestDialect();

        JsonTransform decode = dialect.supplyResponseDecodeTransform();
        JsonSink encode = dialect.supplyResponseEncodeSink(JsonEnvelope.NONE, LlmDialectResponseDispatchTest::discard);

        assertThat(decode, instanceOf(LlmDialectEvent.class));
        assertThat(encode, instanceOf(LlmDialectTerminator.class));
    }

    private static void discard(
        String name,
        DirectBuffer buffer,
        int offset,
        int length)
    {
    }
}
