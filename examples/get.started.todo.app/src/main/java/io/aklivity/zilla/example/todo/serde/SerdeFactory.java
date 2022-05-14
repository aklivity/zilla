/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.zilla.example.todo.serde;

import java.util.Map;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonSerializer;

public final class SerdeFactory
{
    private SerdeFactory()
    {
    }

    public static <T> Serde jsonSerdeFor(Class<T> clazz, boolean isKey)
    {
        var props = Map.of(
            "json.key.type", clazz,
            "json.value.type", clazz
        );

        var ser = new KafkaJsonSerializer<T>();
        ser.configure(props, isKey);

        var de = new KafkaJsonDeserializer<T>();
        de.configure(props, isKey);

        return Serdes.serdeFrom(ser, de);
    }
}
