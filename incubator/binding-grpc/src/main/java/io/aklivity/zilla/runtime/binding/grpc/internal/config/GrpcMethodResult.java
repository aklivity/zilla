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
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;

public class GrpcMethodResult
{
    public final CharSequence service;
    public final CharSequence method;
    public final long grpcTimeout;
    public final String16FW contentType;
    public final String16FW scheme;
    public final String16FW authority;
    public final String16FW te;
    public final Array32FW<GrpcMetadataFW> metadata;

    public GrpcMethodResult(
        CharSequence service,
        CharSequence method,
        long grpcTimeout,
        String16FW contentType,
        String16FW scheme,
        String16FW authority,
        String16FW te,
        Array32FW<GrpcMetadataFW> metadata)
    {
        this.service = service;
        this.method = method;
        this.grpcTimeout = grpcTimeout;
        this.scheme = scheme;
        this.authority = authority;
        this.contentType = contentType;
        this.te = te;
        this.metadata = metadata;
    }
}
