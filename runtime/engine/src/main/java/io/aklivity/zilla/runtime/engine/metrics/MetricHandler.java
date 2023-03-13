package io.aklivity.zilla.runtime.engine.metrics;

import static java.util.Objects.requireNonNull;

import org.agrona.DirectBuffer;

public interface MetricHandler
{
    MetricHandler NO_OP = new MetricHandler()
    {
        @Override
        public void onEvent(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            // no op
        }

        @Override
        public MetricHandler andThen(
            MetricHandler after)
        {
            return requireNonNull(after);
        }
    };

    void onEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length);

    default MetricHandler andThen(
        MetricHandler after)
    {
        requireNonNull(after);
        return (int msgTypeId, DirectBuffer buffer, int index, int length) ->
        {
            onEvent(msgTypeId, buffer, index, length);
            after.onEvent(msgTypeId, buffer, index, length);
        };
    }
}
