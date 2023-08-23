package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.util.List;

public final class KafkaOffsetFetchTopic
{
    final String topic;
    List<Integer> partitions;

    KafkaOffsetFetchTopic(
        String topic,
        List<Integer> partitions)
    {
        this.topic = topic;
        this.partitions = partitions;
    }
}
