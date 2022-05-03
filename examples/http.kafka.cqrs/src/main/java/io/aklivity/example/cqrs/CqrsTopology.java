/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.example.cqrs;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.aklivity.example.cqrs.model.Command;
import io.aklivity.example.cqrs.model.Task;
import io.aklivity.example.cqrs.processor.AcceptCommandProcessorSupplier;
import io.aklivity.example.cqrs.processor.CommandProcessor;
import io.aklivity.example.cqrs.processor.RejectCommandProcessorSupplier;
import io.aklivity.example.cqrs.serde.SerdeFactory;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CqrsTopology
{
    private final Serde<String> stringSerde = Serdes.String();
    private final Serde<byte[]> etagSerde = Serdes.ByteArray();
    private final Serde<Command> commandSerde = SerdeFactory.jsonSerdeFor(Command.class, false);
    private final Serde<Task> taskSerde = SerdeFactory.jsonSerdeFor(Task.class, false);
    private final Serde<Object> responseSerde = SerdeFactory.jsonSerdeFor(Object.class, false);

    @Value("${task.commands.topic}")
    String taskCommandsTopic;

    @Value("${task.snapshots.topic}")
    String taskSnapshotsTopic;

    @Value("${task.replies.topic}")
    String taskRepliesTopic;

    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder)
    {
        final String etagStoreName = "EtagStore";
        final String command = "Command";
        final String commandProcessor = "CommandProcessor";
        final String replyTo = "ReplyTo";
        final String rejectCommandProcessor = "RejectCommandProcessor";
        final String acceptCommandProcessor = "AcceptCommandProcessor";
        final String commandSnapshot = "CommandSnapshot";
        // create store
        final StoreBuilder commandStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(etagStoreName),
                Serdes.String(),
                etagSerde);

        Topology topologyBuilder = streamsBuilder.build();
        topologyBuilder.addSource(command, stringSerde.deserializer(), commandSerde.deserializer(), taskCommandsTopic)
                .addProcessor(commandProcessor, new CommandProcessor(etagStoreName,
                        acceptCommandProcessor, rejectCommandProcessor), command)
                .addProcessor(acceptCommandProcessor,
                        new AcceptCommandProcessorSupplier(etagStoreName, commandSnapshot, replyTo),
                        commandProcessor)
                .addProcessor(rejectCommandProcessor, new RejectCommandProcessorSupplier(replyTo),
                        commandProcessor)
                .addSink(replyTo, taskRepliesTopic, stringSerde.serializer(), responseSerde.serializer(),
                        acceptCommandProcessor, rejectCommandProcessor)
                .addSink(commandSnapshot, taskSnapshotsTopic, stringSerde.serializer(), taskSerde.serializer(),
                        acceptCommandProcessor)
                .addStateStore(commandStoreBuilder, commandProcessor, acceptCommandProcessor);
    }
}
