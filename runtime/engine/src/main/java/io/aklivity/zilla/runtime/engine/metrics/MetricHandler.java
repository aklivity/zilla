package io.aklivity.zilla.runtime.engine.metrics;

import org.agrona.DirectBuffer;

public interface MetricHandler
{
    enum Event
    {
        RECEIVED,
        SENT
    }

    void onEvent(
        int msgTypeId, DirectBuffer buffer, int index, int length);

    default MetricHandler andThen(
        MetricHandler next
    )
    {
        return (int msgTypeId, DirectBuffer buffer, int index, int length) ->
        {
            onEvent(msgTypeId, buffer, index, length);
            next.onEvent(msgTypeId, buffer, index, length);
        };
    }
}
