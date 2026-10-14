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
package io.aklivity.zilla.specs.binding.llm.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmChallengeExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmFlushExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmSectionType;

public class LlmFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new LlmFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateBeginEx()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .contentType("application/json")
            .model("gpt-x")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateBeginExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .contentType("application/json")
            .model("gpt-x")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("openai")
            .contentType("application/json")
            .model("gpt-x")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchBeginExMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("anthropic")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateChallengeEx()
    {
        byte[] bytes = LlmFunctions.challengeEx()
            .typeId(0)
            .dialect("anthropic")
            .build();

        final LlmChallengeExFW challengeEx = new LlmChallengeExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("anthropic", challengeEx.dialect().asString());
    }

    @Test
    public void shouldMatchChallengeEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchChallengeEx()
            .typeId(0)
            .dialect("anthropic")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmChallengeExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("anthropic")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchChallengeExMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchChallengeEx()
            .typeId(0)
            .dialect("anthropic")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmChallengeExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("openai")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateFlushEx()
    {
        byte[] bytes = LlmFunctions.flushEx()
            .typeId(0)
            .maxOutputTokens(2048)
            .build();

        final LlmFlushExFW flushEx = new LlmFlushExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(2048, flushEx.maxOutputTokens());
    }

    @Test
    public void shouldMatchFlushEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx()
            .typeId(0)
            .maxOutputTokens(2048)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmFlushExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .maxOutputTokens(2048)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchFlushExMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx()
            .typeId(0)
            .maxOutputTokens(2048)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmFlushExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .maxOutputTokens(1024)
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFlushExWithoutMaxOutputTokens() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchFlushEx()
            .typeId(0)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmFlushExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateChallengeExWithModel()
    {
        byte[] bytes = LlmFunctions.challengeEx()
            .typeId(0)
            .dialect("anthropic")
            .model("claude-opus-4-1")
            .build();

        final LlmChallengeExFW challengeEx = new LlmChallengeExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("anthropic", challengeEx.dialect().asString());
        assertEquals("claude-opus-4-1", challengeEx.model().asString());
    }

    @Test
    public void shouldGenerateChallengeExWithoutModel()
    {
        byte[] bytes = LlmFunctions.challengeEx()
            .typeId(0)
            .dialect("anthropic")
            .build();

        final LlmChallengeExFW challengeEx = new LlmChallengeExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertNull(challengeEx.model().asString());
    }

    @Test
    public void shouldMatchChallengeExWithModel() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchChallengeEx()
            .typeId(0)
            .dialect("anthropic")
            .model("claude-opus-4-1")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmChallengeExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("anthropic")
            .model("claude-opus-4-1")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchChallengeExModelMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchChallengeEx()
            .typeId(0)
            .dialect("anthropic")
            .model("claude-opus-4-1")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmChallengeExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("anthropic")
            .model("claude-haiku-4-5")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchChallengeExModelExpectedButAbsent() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchChallengeEx()
            .typeId(0)
            .dialect("anthropic")
            .model("claude-opus-4-1")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmChallengeExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("anthropic")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchChallengeExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchChallengeEx().build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmChallengeExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("openai")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateDataExWithAllFields()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .type("USER_TEXT")
            .message(2)
            .attribute("media-type", "image/png")
            .attribute("source", "url")
            .extension("ext")
            .build();

        LlmSectionDataExFW dataEx = new LlmDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length).section();
        assertEquals(LlmSectionType.USER_TEXT, dataEx.type().get());
        assertEquals(2, dataEx.message());
        assertEquals(Map.of("media-type", "image/png", "source", "url"), attributesOf(dataEx));
        assertEquals(3, dataEx.extension().sizeof());
    }

    @Test
    public void shouldGenerateDataExWithAttributesBeforeExtension()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .type("TOOL_RESULT")
            .attribute("tool-call-id", "call_1")
            .extension("ext")
            .build();

        LlmSectionDataExFW dataEx = new LlmDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length).section();
        assertEquals(Map.of("tool-call-id", "call_1"), attributesOf(dataEx));
        assertEquals("ext", dataEx.extension().buffer()
            .getStringWithoutLengthUtf8(dataEx.extension().offset(), dataEx.extension().sizeof()));
    }

    @Test
    public void shouldGenerateDataExWithAttributesAfterExtension()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .type("TOOL_RESULT")
            .extension("ext")
            .attribute("tool-call-id", "call_1")
            .build();

        LlmSectionDataExFW dataEx = new LlmDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length).section();
        assertEquals(Map.of("tool-call-id", "call_1"), attributesOf(dataEx));
        assertEquals(3, dataEx.extension().sizeof());
    }

    @Test
    public void shouldGenerateDataExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchDataExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .section(s -> s
                .type(t -> t.set(LlmSectionType.USER_TEXT)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExType() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .type("USER_TEXT")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .section(s -> s
                .type(t -> t.set(LlmSectionType.USER_TEXT)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExNullFields() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .typeNull()
            .attributesNull()
            .extensionNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        final LlmDataExFW dataEx = new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .section(s -> s.message(-1))
            .build();
        byteBuf.limit(dataEx.limit());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .section(s -> s.message(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExTypeMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .type("USER_TEXT")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .section(s -> s
                .type(t -> t.set(LlmSectionType.ASSISTANT_TEXT)))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExTypeNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .typeNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .section(s -> s
                .type(t -> t.set(LlmSectionType.USER_TEXT)))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExMessageTypeAttributesAndExtension() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .type("TOOL_RESULT")
            .message(3)
            .attribute("tool-call-id", "call_1")
            .extension("ext")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .type("TOOL_RESULT")
            .message(3)
            .attribute("tool-call-id", "call_1")
            .extension("ext")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchDataExAttributesInAnyOrder() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .attribute("source", "url")
            .attribute("media-type", "image/png")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .attribute("media-type", "image/png")
            .attribute("source", "url")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExAttributesWithExtraAttribute() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .attribute("source", "url")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .attribute("source", "url")
            .attribute("media-type", "image/png")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExAttributesWithMissingAttribute() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .attribute("source", "url")
            .attribute("media-type", "image/png")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .attribute("source", "url")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldDefaultDataExMessageAndSlots()
    {
        byte[] bytes = LlmFunctions.dataEx()
            .typeId(0)
            .type("USER_TEXT")
            .build();

        LlmSectionDataExFW dataEx = new LlmDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length).section();
        assertEquals(-1, dataEx.message());
        assertEquals(Map.of(), attributesOf(dataEx));
        assertEquals(0, dataEx.extension().sizeof());
    }

    @Test
    public void shouldFailMatchDataExMessageMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .message(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .message(2)
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExAttributesMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .attribute("source", "url")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .attribute("source", "reference")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExAttributesNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .attributesNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .attribute("source", "url")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExExtensionMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .extension("a")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .extension("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchDataExExtensionNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchDataEx()
            .typeId(0)
            .extensionNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.dataEx()
            .typeId(0)
            .extension("a")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExExtension() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .extension("ext")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .extension("ext")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExExtensionNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .extensionNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchBeginExExtensionMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("openai")
            .extension("a")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .extension("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAndMatchResetExParam() throws Exception
    {
        byte[] bytes = LlmFunctions.resetEx()
            .typeId(0)
            .status(400)
            .type("invalid_request_error")
            .param("messages[3]")
            .build();

        LlmResetExFW resetEx = new LlmResetExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("messages[3]", resetEx.error().param().asString());

        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeId(0)
            .param("messages[3]")
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldMatchResetExParamNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeId(0)
            .paramNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .status(400)
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchResetExParamMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeId(0)
            .param("a")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .param("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAndMatchAbortExParam() throws Exception
    {
        byte[] bytes = LlmFunctions.abortEx()
            .typeId(0)
            .status(400)
            .type("invalid_request_error")
            .param("messages[3]")
            .build();

        LlmAbortExFW abortEx = new LlmAbortExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("messages[3]", abortEx.error().param().asString());

        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .param("messages[3]")
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldMatchAbortExParamNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .paramNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.abortEx()
            .typeId(0)
            .status(400)
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateEndExWithAllFields()
    {
        byte[] bytes = LlmFunctions.endEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(15)
            .reasoningTokens(8)
            .totalTokens(40)
            .nativeUsage("{\"prompt_tokens\":25,\"completion_tokens\":15}")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateEndExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.endEx()
            .typeId(0)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchEndExTokens() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(15)
            .reasoningTokens(8)
            .totalTokens(40)
            .nativeUsage("{\"prompt_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(5).cacheReadTokens(10).outputTokens(15)
                .reasoningTokens(8).totalTokens(40).nativeUsage("{\"prompt_tokens\":25}"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchEndExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(15)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchEndExNativeUsageNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExInputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .inputTokens(25)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(30).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExCacheWriteTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .cacheWriteTokens(5)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(9).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExCacheReadTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .cacheReadTokens(10)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(20).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExOutputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .outputTokens(15)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(20)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExReasoningTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .reasoningTokens(8)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(2).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExTotalTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .totalTokens(40)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(10))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExNativeUsageMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .nativeUsage("{\"prompt_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"prompt_tokens\":30}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExNativeUsageNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"prompt_tokens\":30}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAbortExWithAllFields()
    {
        byte[] bytes = LlmFunctions.abortEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(5)
            .reasoningTokens(2)
            .totalTokens(30)
            .nativeUsage("{\"input_tokens\":25}")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateAbortExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.abortEx()
            .typeId(0)
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchAbortExTokens() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .inputTokens(25)
            .cacheWriteTokens(5)
            .cacheReadTokens(10)
            .outputTokens(5)
            .reasoningTokens(2)
            .totalTokens(30)
            .nativeUsage("{\"input_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(5).cacheReadTokens(10).outputTokens(5)
                .reasoningTokens(2).totalTokens(30).nativeUsage("{\"input_tokens\":25}"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAbortExWithoutConstraints() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(25).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAbortExNativeUsageNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExInputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .inputTokens(25)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(5).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExCacheWriteTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .cacheWriteTokens(5)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(9).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExCacheReadTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .cacheReadTokens(10)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(20).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExOutputTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .outputTokens(5)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(9)
                .reasoningTokens(-1).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExReasoningTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .reasoningTokens(2)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(7).totalTokens(-1))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExTotalTokensMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .totalTokens(30)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(10))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExNativeUsageMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .nativeUsage("{\"input_tokens\":25}")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"input_tokens\":5}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExNativeUsageNotNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .nativeUsageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .usage(u -> u.inputTokens(-1).cacheWriteTokens(-1).cacheReadTokens(-1).outputTokens(-1)
                .reasoningTokens(-1).totalTokens(-1).nativeUsage("{\"input_tokens\":5}"))
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAbortExWithError()
    {
        byte[] bytes = LlmFunctions.abortEx()
            .typeId(0)
            .status(500)
            .type("api_error")
            .message("Internal error")
            .build();

        LlmAbortExFW abortEx = new LlmAbortExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals(500, abortEx.error().status());
        assertEquals("api_error", abortEx.error().type().asString());
        assertEquals("Internal error", abortEx.error().message().asString());
        assertEquals(-1, abortEx.usage().inputTokens());
    }

    @Test
    public void shouldMatchAbortExError() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .status(500)
            .type("api_error")
            .message("Internal error")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.abortEx()
            .typeId(0)
            .status(500)
            .type("api_error")
            .message("Internal error")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchAbortExErrorNone() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .errorNone()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.abortEx()
            .typeId(0)
            .inputTokens(3)
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExErrorNone() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .errorNone()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.abortEx()
            .typeId(0)
            .message("failed")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchAbortExErrorStatusMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchAbortEx()
            .typeId(0)
            .status(429)
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.abortEx()
            .typeId(0)
            .status(500)
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResetEx()
    {
        byte[] bytes = LlmFunctions.resetEx()
            .typeId(0)
            .status(429)
            .type("rate_limit_error")
            .message("Too many requests")
            .build();

        LlmResetExFW resetEx = new LlmResetExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals(0, resetEx.typeId());
        assertEquals(429, resetEx.error().status());
        assertEquals("rate_limit_error", resetEx.error().type().asString());
        assertEquals("Too many requests", resetEx.error().message().asString());
    }

    @Test
    public void shouldGenerateResetExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.resetEx()
            .typeId(0)
            .build();

        LlmResetExFW resetEx = new LlmResetExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals(-1, resetEx.error().status());
        assertNull(resetEx.error().type().asString());
        assertNull(resetEx.error().message().asString());
    }

    @Test
    public void shouldMatchResetEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeId(0)
            .status(429)
            .type("rate_limit_error")
            .message("Too many requests")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .status(429)
            .type("rate_limit_error")
            .message("Too many requests")
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchResetExNulls() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeId(0)
            .status(502)
            .typeNull()
            .messageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .status(502)
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldNotMatchEmptyResetEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldFailMatchResetExTypeIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeId(1)
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchResetExStatusMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .status(400)
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .status(429)
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchResetExTypeMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .type("a")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .type("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchResetExTypeNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .typeNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .type("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchResetExMessageMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .message("a")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .message("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchResetExMessageNullMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchResetEx()
            .messageNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.resetEx()
            .typeId(0)
            .message("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAndMatchBeginExId() throws Exception
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .model("gpt-x")
            .id("chatcmpl_1")
            .build();

        LlmBeginExFW beginEx = new LlmBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("chatcmpl_1", beginEx.id().asString());

        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .id("chatcmpl_1")
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldFailMatchBeginExIdMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .id("a")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .id("b")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAndMatchEndExFinishReason() throws Exception
    {
        byte[] bytes = LlmFunctions.endEx()
            .typeId(0)
            .inputTokens(1)
            .finishReason("tool-call")
            .build();

        LlmEndExFW endEx = new LlmEndExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tool-call", endEx.finishReason().asString());

        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .finishReason("tool-call")
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldMatchEndExFinishReasonNull() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .finishReasonNull()
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.endEx()
            .typeId(0)
            .inputTokens(1)
            .build());

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchEndExFinishReasonMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchEndEx()
            .typeId(0)
            .finishReason("stop")
            .build();

        ByteBuffer byteBuf = ByteBuffer.wrap(LlmFunctions.endEx()
            .typeId(0)
            .finishReason("length")
            .build());

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchBeginExWithoutBinaryExtension() throws Exception
    {
        final byte[] beginEx = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("openai")
            .build();

        final BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .extensionNull()
            .build();

        assertNotNull(matcher.match(ByteBuffer.wrap(beginEx)));
    }

    @Test
    public void shouldGenerateAndMatchDialectDataEx() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .build();

        final LlmDataExFW decoded = new LlmDataExFW().wrap(new UnsafeBufferEx(dataEx), 0, dataEx.length);

        assertEquals(LlmDataExFW.KIND_DIALECT, decoded.kind());
        assertNotNull(LlmFunctions.matchDialectDataEx().typeId(0).build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldMatchDialectDataExWithoutConstraints() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(1)
            .build();

        assertNotNull(LlmFunctions.matchDialectDataEx().build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldDefaultDialectDataExCompleteToFalse() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .build();

        final LlmDataExFW decoded = new LlmDataExFW().wrap(new UnsafeBufferEx(dataEx), 0, dataEx.length);

        assertEquals(0, decoded.dialect().complete());
        assertNotNull(LlmFunctions.matchDialectDataEx().typeId(0).complete(false).build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldGenerateAndMatchDialectDataExComplete() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .complete(true)
            .build();

        final LlmDataExFW decoded = new LlmDataExFW().wrap(new UnsafeBufferEx(dataEx), 0, dataEx.length);

        assertEquals(1, decoded.dialect().complete());
        assertNotNull(LlmFunctions.matchDialectDataEx().typeId(0).complete(true).build().match(ByteBuffer.wrap(dataEx)));
        assertNotNull(LlmFunctions.matchDialectDataEx().typeId(0).build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldFailMatchDialectDataExCompleteMismatch() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .complete(true)
            .build();

        final BytesMatcher matcher = LlmFunctions.matchDialectDataEx()
            .typeId(0)
            .complete(false)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldGenerateAndMatchDialectDataExAttributes() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .attribute("type", "content_block_delta")
            .build();

        final LlmDataExFW decoded = new LlmDataExFW().wrap(new UnsafeBufferEx(dataEx), 0, dataEx.length);

        assertEquals(1, decoded.dialect().attributes().fieldCount());
        assertNotNull(LlmFunctions.matchDialectDataEx()
            .typeId(0)
            .attribute("type", "content_block_delta")
            .build()
            .match(ByteBuffer.wrap(dataEx)));
        assertNotNull(LlmFunctions.matchDialectDataEx().typeId(0).build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldFailMatchDialectDataExAttributesMismatch() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .attribute("type", "content_block_delta")
            .build();

        final BytesMatcher matcher = LlmFunctions.matchDialectDataEx()
            .typeId(0)
            .attribute("type", "message_stop")
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldFailMatchDialectDataExTypeIdMismatch() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(1)
            .build();

        final BytesMatcher matcher = LlmFunctions.matchDialectDataEx()
            .typeId(2)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchSectionAsDialectDataEx() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dataEx()
            .typeId(0)
            .type("USER_TEXT")
            .build();

        assertThrows(Exception.class, () -> LlmFunctions.matchDialectDataEx().build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldNotMatchDialectAsSectionDataEx() throws Exception
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .build();

        assertThrows(Exception.class, () -> LlmFunctions.matchDataEx().build().match(ByteBuffer.wrap(dataEx)));
    }

    @Test
    public void shouldGenerateDialectDataExAsDialectKind()
    {
        final byte[] dataEx = LlmFunctions.dialectDataEx()
            .typeId(0)
            .build();

        final LlmDataExFW decoded = new LlmDataExFW().wrap(new UnsafeBufferEx(dataEx), 0, dataEx.length);

        assertEquals(LlmDataExFW.KIND_DIALECT, decoded.kind());
    }

    private static Map<String, String> attributesOf(
        LlmSectionDataExFW dataEx)
    {
        final Map<String, String> attributes = new HashMap<>();
        dataEx.attributes().forEach(a -> attributes.put(a.name().asString(), a.value().asString()));
        return attributes;
    }
}
