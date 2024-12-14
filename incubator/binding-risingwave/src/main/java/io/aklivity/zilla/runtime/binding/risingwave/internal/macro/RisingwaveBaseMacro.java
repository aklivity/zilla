package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.nio.ByteOrder;
import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.risingwave.internal.types.String32FW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public abstract class RisingwaveBaseMacro
{
    protected static final String ZILLA_CORRELATION_ID_OLD = "zilla_correlation_id";
    protected static final String ZILLA_IDENTITY_OLD = "zilla_identity";
    protected static final String ZILLA_TIMESTAMP_OLD = "zilla_timestamp";

    protected static final String ZILLA_IDENTITY = "GENERATED ALWAYS AS IDENTITY";
    protected static final String ZILLA_TIMESTAMP = "GENERATED ALWAYS AS NOW";

    protected static final Map<String, String> ZILLA_MAPPINGS = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_MAPPINGS.put(ZILLA_IDENTITY, "INCLUDE header 'zilla:identity' AS %s\n");
        ZILLA_MAPPINGS.put(ZILLA_TIMESTAMP, "INCLUDE timestamp AS %s\n");
    }

    protected static final Map<String, String> ZILLA_MAPPINGS_OLD = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_MAPPINGS_OLD.put(ZILLA_CORRELATION_ID_OLD, "INCLUDE header 'zilla:correlation-id' AS %s\n");
        ZILLA_MAPPINGS_OLD.put(ZILLA_IDENTITY_OLD, "INCLUDE header 'zilla:identity' AS %s\n");
        ZILLA_MAPPINGS_OLD.put(ZILLA_TIMESTAMP_OLD, "INCLUDE timestamp AS %s\n");
    }
}
