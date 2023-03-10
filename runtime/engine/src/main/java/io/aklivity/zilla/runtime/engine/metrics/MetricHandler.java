package io.aklivity.zilla.runtime.engine.metrics;

import static java.util.Objects.requireNonNull;

import org.agrona.DirectBuffer;

public interface MetricHandler
{
    void onEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length);

    default MetricHandler andThen(
        MetricHandler after
    )
    {
        requireNonNull(after);
        return (int msgTypeId, DirectBuffer buffer, int index, int length) ->
        {
            onEvent(msgTypeId, buffer, index, length);
            after.onEvent(msgTypeId, buffer, index, length);
        };
    }
}
