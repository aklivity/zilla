/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcKind;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;


public class GrpcMethodResolver
{
    public final CharSequence service;
    public final CharSequence method;
    public final CharSequence contentType;
    public final long grpcTimeout;
    public final String16FW scheme;
    public final String16FW authority;
    public final GrpcKind request;
    public final GrpcKind response;
    public final Array32FW<GrpcMetadataFW> metadata;

    public GrpcMethodResolver(
        CharSequence service,
        CharSequence method,
        CharSequence contentType,
        long grpcTimeout,
        String16FW scheme,
        String16FW authority,
        Array32FW<GrpcMetadataFW> metadata,
        GrpcKind request,
        GrpcKind response)
    {
        this.service = service;
        this.method = method;
        this.grpcTimeout = grpcTimeout;
        this.scheme = scheme;
        this.authority = authority;
        this.contentType = contentType;
        this.metadata = metadata;
        this.request = request;
        this.response = response;
    }
}
