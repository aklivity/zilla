package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import static io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi.Kind.BINDING;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.KafkaGrpcBinding;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class KafkaGrpcOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    public static final KafkaAckMode ACKS_DEFAULT = KafkaAckMode.IN_SYNC_REPLICAS;
    private static final String ENTRY_NAME = "entry";
    private static final String ACKS_NAME = "acks";

    @Override
    public OptionsConfigAdapterSpi.Kind kind()
    {
        return BINDING;
    }

    @Override
    public String type()
    {
        return KafkaGrpcBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        KafkaGrpcOptionsConfig kafkaGrpcOptions = (KafkaGrpcOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaGrpcOptions.acks != ACKS_DEFAULT)
        {
            object.add(ACKS_NAME, kafkaGrpcOptions.acks.name().toLowerCase());
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        KafkaAckMode newProduceAcks = object.containsKey(ACKS_NAME)
            ? KafkaAckMode.valueOf(object.getString(ACKS_NAME).toUpperCase())
            : ACKS_DEFAULT;

        String newEntry = object.getString(ENTRY_NAME);

        return new KafkaGrpcOptionsConfig(newEntry, newProduceAcks);
    }
}
