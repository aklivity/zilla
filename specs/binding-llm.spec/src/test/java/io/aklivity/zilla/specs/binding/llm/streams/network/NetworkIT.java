/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.llm.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/llm/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/openai.chat.completions/client",
        "${net}/openai.chat.completions/server"})
    public void shouldExchangeOpenaiChatCompletions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.chat.completions.stream/client",
        "${net}/openai.chat.completions.stream/server"})
    public void shouldExchangeOpenaiChatCompletionsStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.chat.completions.stream.include.usage/client",
        "${net}/openai.chat.completions.stream.include.usage/server"})
    public void shouldExchangeOpenaiChatCompletionsStreamIncludeUsage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.chat.completions.stream.tool.call/client",
        "${net}/openai.chat.completions.stream.tool.call/server"})
    public void shouldExchangeOpenaiChatCompletionsStreamToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.completions/client",
        "${net}/openai.completions/server"})
    public void shouldExchangeOpenaiCompletions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.messages/client",
        "${net}/anthropic.messages/server"})
    public void shouldExchangeAnthropicMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.messages.stream/client",
        "${net}/anthropic.messages.stream/server"})
    public void shouldExchangeAnthropicMessagesStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.messages.stream.tool.use/client",
        "${net}/anthropic.messages.stream.tool.use/server"})
    public void shouldExchangeAnthropicMessagesStreamToolUse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.messages.stream.multi.block/client",
        "${net}/anthropic.messages.stream.multi.block/server"})
    public void shouldExchangeAnthropicMessagesStreamMultiBlock() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.chat/client",
        "${net}/ollama.chat/server"})
    public void shouldExchangeOllamaChat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.chat.stream/client",
        "${net}/ollama.chat.stream/server"})
    public void shouldExchangeOllamaChatStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.generate/client",
        "${net}/ollama.generate/server"})
    public void shouldExchangeOllamaGenerate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.generate.stream/client",
        "${net}/ollama.generate.stream/server"})
    public void shouldExchangeOllamaGenerateStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.path.openai.chat.completions/client",
        "${net}/detect.path.openai.chat.completions/server"})
    public void shouldDetectPathOpenaiChatCompletions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.path.openai.completions/client",
        "${net}/detect.path.openai.completions/server"})
    public void shouldDetectPathOpenaiCompletions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.path.anthropic.messages/client",
        "${net}/detect.path.anthropic.messages/server"})
    public void shouldDetectPathAnthropicMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.header.anthropic.version/client",
        "${net}/detect.header.anthropic.version/server"})
    public void shouldDetectHeaderAnthropicVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.header.anthropic.x.api.key/client",
        "${net}/detect.header.anthropic.x.api.key/server"})
    public void shouldDetectHeaderAnthropicXApiKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.path.ollama.chat/client",
        "${net}/detect.path.ollama.chat/server"})
    public void shouldDetectPathOllamaChat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.path.ollama.generate/client",
        "${net}/detect.path.ollama.generate/server"})
    public void shouldDetectPathOllamaGenerate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/detect.ambiguous.rejected/client",
        "${net}/detect.ambiguous.rejected/server"})
    public void shouldDetectAmbiguousRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.system.prompt.prefix/client",
        "${net}/openai.system.prompt.prefix/server"})
    public void shouldInjectOpenaiSystemPromptPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.system.prompt.replace/client",
        "${net}/openai.system.prompt.replace/server"})
    public void shouldInjectOpenaiSystemPromptReplace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.system.prompt.suffix/client",
        "${net}/openai.system.prompt.suffix/server"})
    public void shouldInjectOpenaiSystemPromptSuffix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.system.prompt.prefix/client",
        "${net}/anthropic.system.prompt.prefix/server"})
    public void shouldInjectAnthropicSystemPromptPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.system.prompt.replace/client",
        "${net}/anthropic.system.prompt.replace/server"})
    public void shouldInjectAnthropicSystemPromptReplace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.system.prompt.suffix/client",
        "${net}/anthropic.system.prompt.suffix/server"})
    public void shouldInjectAnthropicSystemPromptSuffix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.system.prompt.prefix/client",
        "${net}/ollama.system.prompt.prefix/server"})
    public void shouldInjectOllamaSystemPromptPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.auth.bearer.injected/client",
        "${net}/openai.auth.bearer.injected/server"})
    public void shouldInjectOpenaiAuthBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.auth.x.api.key.injected/client",
        "${net}/anthropic.auth.x.api.key.injected/server"})
    public void shouldInjectAnthropicAuthXApiKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.auth.none/client",
        "${net}/ollama.auth.none/server"})
    public void shouldExchangeOllamaAuthNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/auth.inbound.stripped/client",
        "${net}/auth.inbound.stripped/server"})
    public void shouldStripInboundAuth() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/anthropic.max.tokens.default.injected/client",
        "${net}/anthropic.max.tokens.default.injected/server"})
    public void shouldInjectAnthropicMaxTokensDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/openai.max.tokens.optional/client",
        "${net}/openai.max.tokens.optional/server"})
    public void shouldOmitOpenaiMaxTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ollama.max.tokens.optional/client",
        "${net}/ollama.max.tokens.optional/server"})
    public void shouldOmitOllamaMaxTokens() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/max.tokens.capped/client",
        "${net}/max.tokens.capped/server"})
    public void shouldCapMaxTokens() throws Exception
    {
        k3po.finish();
    }
}
