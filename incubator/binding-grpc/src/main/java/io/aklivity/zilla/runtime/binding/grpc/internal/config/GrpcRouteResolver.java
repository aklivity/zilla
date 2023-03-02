package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;

public class GrpcRouteResolver
{
    public final long id;
    public final CharSequence contentType;
    public final Map<String8FW, String16FW> metadata;

    public GrpcRouteResolver(
        long id,
        CharSequence contentType,
        Map<String8FW, String16FW> metadata)
    {

        this.id = id;
        this.contentType = contentType;
        this.metadata = metadata;
    }
}
