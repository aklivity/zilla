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

import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// Wire-format-identical to LlmOpenaiDialect, like LlmSignedTestDialect, but whose signer always throws --
// standing in for a real signer's documented "credentials not yet available" failure mode (see
// LlmRequestSigner#sign's javadoc) -- so an IT can exercise LlmClientFactory's own recovery from a signer
// that cannot currently produce a signature, without needing a real signing upstream or a background
// credential fetch race.
final class LlmSigningUnavailableTestDialect implements LlmDialect
{
    private final LlmDialect delegate = new LlmOpenaiDialect();

    @Override
    public String name()
    {
        return "test-signing-unavailable";
    }

    // never participates in automatic detection -- see LlmSignedTestDialect#detect for why
    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        return false;
    }

    @Override
    public String requestPath(
        String basePath)
    {
        return delegate.requestPath(basePath);
    }

    @Override
    public String credentialsHeader()
    {
        return delegate.credentialsHeader();
    }

    @Override
    public String unauthorizedBody()
    {
        return delegate.unauthorizedBody();
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return delegate.supplyDecoder(kind, envelope);
    }

    @Override
    public JsonTransform supplyExtractor(
        Kind kind,
        JsonEnvelope envelope)
    {
        return delegate.supplyExtractor(kind, envelope);
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return delegate.supplyEncoder(kind, envelope);
    }

    @Override
    public JsonTransform supplyResponseDecodeTransform()
    {
        return delegate.supplyResponseDecodeTransform();
    }

    @Override
    public JsonSink supplyResponseEncodeSink(
        JsonEnvelope envelope,
        LlmNativeEventOutput output)
    {
        return delegate.supplyResponseEncodeSink(envelope, output);
    }

    @Override
    public JsonTransform supplySchemaValidator(
        Kind kind)
    {
        return delegate.supplySchemaValidator(kind);
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return delegate.terminator(kind);
    }

    @Override
    public LlmRequestSigner signer()
    {
        return LlmSigningUnavailableTestDialect::sign;
    }

    private static List<Map.Entry<String, String>> sign(
        String method,
        String scheme,
        String authority,
        String path,
        List<Map.Entry<String, String>> headers,
        DirectBuffer body,
        int bodyOffset,
        int bodyLength)
    {
        throw new IllegalStateException("test signer not yet available");
    }
}
