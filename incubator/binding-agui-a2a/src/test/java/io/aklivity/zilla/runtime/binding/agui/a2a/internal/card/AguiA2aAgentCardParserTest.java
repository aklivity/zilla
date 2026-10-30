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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.card;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class AguiA2aAgentCardParserTest
{
    private static final String CARD_PREFIX = "{\"name\":\"Weather Agent\",\"description\":\"Answers weather questions\"," +
        "\"supportedInterfaces\":[{\"url\":\"http://localhost:9090/a2a\",\"protocolBinding\":\"JSONRPC\"," +
        "\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\",";
    private static final String CARD_SUFFIX = ",\"defaultOutputModes\":[\"text/plain\"],\"skills\":[{\"id\":\"weather\"," +
        "\"name\":\"Weather\",\"description\":\"Current weather\",\"tags\":[\"weather\"]}]}";

    private final AguiA2aAgentCardParser parser = new AguiA2aAgentCardParser();
    private final UnsafeBufferEx input = new UnsafeBufferEx(new byte[8192]);

    @Test
    public void shouldSelectMessageStreamWhenStreaming()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"text/plain\",\"image/png\"]"));

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_STREAM));
    }

    @Test
    public void shouldSelectMessageSendWhenStreamingDisabled()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":false}", "[\"text/plain\"]"));

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_SEND));
    }

    @Test
    public void shouldSelectMessageSendWhenStreamingAbsent()
    {
        final AguiA2aAgentCard card = parse(card("{}", "[\"text/plain\"]"));

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_SEND));
    }

    @Test
    public void shouldSelectMessageSendWhenCapabilitiesAbsent()
    {
        final AguiA2aAgentCard card = parse(CARD_PREFIX.substring(0, CARD_PREFIX.length() - 1) +
            ",\"defaultInputModes\":[\"text/plain\"]" + CARD_SUFFIX);

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_SEND));
    }

    @Test
    public void shouldIgnoreStreamingOutsideCapabilities()
    {
        final AguiA2aAgentCard card = parse(CARD_PREFIX + "\"streaming\":true,\"capabilities\":" +
            "{\"extensions\":[{\"uri\":\"x\",\"params\":{\"streaming\":true}}]},\"defaultInputModes\":[\"text/plain\"]" +
            CARD_SUFFIX);

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_SEND));
    }

    @Test
    public void shouldAcceptListedInputModes()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"text/plain\",\"image/png\"]"));

        assertThat(card.acceptsInput("image/png"), is(true));
        assertThat(card.acceptsInput("image/jpeg"), is(false));
        assertThat(card.acceptsInput("application/json"), is(false));
        assertThat(card.acceptsAdvisory(), is(false));
    }

    @Test
    public void shouldAcceptSubtypeWildcardInputModes()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"text/plain\",\"image/*\"]"));

        assertThat(card.acceptsInput("image/png"), is(true));
        assertThat(card.acceptsInput("image/jpeg"), is(true));
        assertThat(card.acceptsInput("audio/wav"), is(false));
        assertThat(card.acceptsInput("imagex/png"), is(false));
    }

    @Test
    public void shouldAcceptAnyInputModeWildcard()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"*/*\"]"));

        assertThat(card.acceptsInput("audio/wav"), is(true));
        assertThat(card.acceptsAdvisory(), is(true));
    }

    @Test
    public void shouldAcceptAdvisoryWhenJsonListed()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"text/plain\",\"application/json\"]"));

        assertThat(card.acceptsAdvisory(), is(true));
    }

    @Test
    public void shouldAcceptAdvisoryWhenApplicationWildcardListed()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"text/plain\",\"application/*\"]"));

        assertThat(card.acceptsAdvisory(), is(true));
        assertThat(card.acceptsInput("application/pdf"), is(true));
        assertThat(card.acceptsInput("image/png"), is(false));
    }

    @Test
    public void shouldNotAcceptAdvisoryForTextOnlyCard()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"text/plain\"]"));

        assertThat(card.acceptsAdvisory(), is(false));
    }

    @Test
    public void shouldIgnoreMediaTypeParametersAndCase()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"Application/JSON; charset=utf-8\"]"));

        assertThat(card.acceptsInput("application/json"), is(true));
        assertThat(card.acceptsInput("APPLICATION/json;charset=utf-8"), is(true));
    }

    @Test
    public void shouldNotAcceptUnknownMediaType()
    {
        final AguiA2aAgentCard card = parse(card("{\"streaming\":true}", "[\"*/*\"]"));

        assertThat(card.acceptsInput(null), is(false));
    }

    @Test
    public void shouldNotAcceptWhenInputModesAbsent()
    {
        final AguiA2aAgentCard card = parse(CARD_PREFIX + "\"capabilities\":{\"streaming\":true}" + CARD_SUFFIX);

        assertThat(card.acceptsInput("image/png"), is(false));
        assertThat(card.acceptsAdvisory(), is(false));
    }

    @Test
    public void shouldAcceptSkillInputModes()
    {
        final AguiA2aAgentCard card = parse(CARD_PREFIX + "\"capabilities\":{\"streaming\":true}," +
            "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"text/plain\"],\"skills\":[" +
            "{\"id\":\"weather\",\"tags\":[\"image/gif\"],\"inputModes\":[\"image/png\"]}," +
            "{\"id\":\"forecast\",\"input_modes\":[\"application/json\"]}]}");

        assertThat(card.acceptsInput("image/png"), is(true));
        assertThat(card.acceptsInput("application/json"), is(true));
        assertThat(card.acceptsInput("image/gif"), is(false));
    }

    @Test
    public void shouldParseWindowedCardAssembled()
    {
        final byte[] bytes = card("{\"streaming\":true}", "[\"text/plain\",\"image/*\"]").getBytes(UTF_8);
        final int split = bytes.length / 2;
        input.putBytes(0, bytes, 0, split);
        input.putBytes(split, bytes, split, bytes.length - split);

        final AguiA2aAgentCard card = parser.parse(input, 0, bytes.length);

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_STREAM));
        assertThat(card.acceptsInput("image/png"), is(true));
    }

    @Test
    public void shouldReuseParserAcrossCards()
    {
        parse(card("{\"streaming\":true}", "[\"image/*\"]"));
        final AguiA2aAgentCard card = parse(card("{}", "[\"text/plain\"]"));

        assertThat(card.operation(), is(AguiA2aOperation.MESSAGE_SEND));
        assertThat(card.acceptsInput("image/png"), is(false));
    }

    private AguiA2aAgentCard parse(
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        input.putBytes(0, bytes);
        return parser.parse(input, 0, bytes.length);
    }

    private static String card(
        String capabilities,
        String inputModes)
    {
        return CARD_PREFIX + "\"capabilities\":" + capabilities + ",\"defaultInputModes\":" + inputModes + CARD_SUFFIX;
    }
}
