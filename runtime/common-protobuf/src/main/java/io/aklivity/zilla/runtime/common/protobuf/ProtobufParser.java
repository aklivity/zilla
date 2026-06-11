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
package io.aklivity.zilla.runtime.common.protobuf;

/**
 * The driver of a {@code common-protobuf} pipeline — peer to {@code common-json}'s parser. A
 * schema-bound parser ({@link StreamingProtobuf#parser(ProtobufSchema, String)}) decodes a message
 * into a typed event stream; a schema-free parser ({@link StreamingProtobuf#parser()}) tokenizes the
 * wire into generic events. Reuse a single instance per worker thread; {@link #stream()} begins a
 * pipeline pumped by this parser, to which stages are appended with {@link ProtobufStream#transform}
 * and which is terminated with {@link ProtobufStream#into}.
 */
public interface ProtobufParser
{
    ProtobufStream stream();
}
