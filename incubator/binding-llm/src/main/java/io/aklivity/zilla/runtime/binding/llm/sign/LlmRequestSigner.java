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
package io.aklivity.zilla.runtime.binding.llm.sign;

import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;

/**
 * Computes the additional headers required to authenticate a fully-buffered outbound request to its upstream,
 * for a {@code kind: client} binding whose upstream requires a signature computed over the complete request --
 * method, path, headers, and body -- rather than a single static credential value carried in one header.
 * <p>
 * Because the signature covers the complete body, a stream with a configured signer buffers its entire request
 * before opening the network connection, rather than streaming the request as it arrives.
 * </p>
 * <p>
 * Obtained from the binding's own configured {@link LlmDialect#signer()}, since requiring this kind of
 * signature is a fixed fact of a dialect's upstream, not a separately-selectable concern.
 * </p>
 */
public interface LlmRequestSigner
{
    /**
     * Signs a fully-buffered outbound request.
     *
     * @param method      the request method, e.g. {@code POST}
     * @param scheme      the request scheme, e.g. {@code https}
     * @param authority   the request authority, e.g. {@code host:port}
     * @param path        the request path, including any query string
     * @param headers     the request's headers before signing, in wire order
     * @param body        the buffer holding the complete request body
     * @param bodyOffset  the offset of the request body within {@code body}
     * @param bodyLength  the length of the request body
     * @return the headers to add to the request before it is sent, in the order they should be written
     */
    List<Map.Entry<String, String>> sign(
        String method,
        String scheme,
        String authority,
        String path,
        List<Map.Entry<String, String>> headers,
        DirectBuffer body,
        int bodyOffset,
        int bodyLength);
}
