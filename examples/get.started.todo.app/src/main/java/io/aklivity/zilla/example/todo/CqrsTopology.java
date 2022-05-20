/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.zilla.example.todo;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.aklivity.zilla.example.todo.model.Task;
import io.aklivity.zilla.example.todo.processor.AcceptCommandProcessorSupplier;
import io.aklivity.zilla.example.todo.processor.CommandProcessorSupplier;
import io.aklivity.zilla.example.todo.processor.RejectCommandProcessorSupplier;
import io.aklivity.zilla.example.todo.serde.CommandJsonDeserializer;
import io.aklivity.zilla.example.todo.serde.SerdeFactory;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CqrsTopology
{
    private final Serde<String> stringSerde = Serdes.String();

    private CommandJsonDeserializer commandDeserializer = new CommandJsonDeserializer();
    private final Serde<String> etagSerde = Serdes.String();
    private final Serde<Task> taskSerde = SerdeFactory.jsonSerdeFor(Task.class, false);
    private final Serde<String> responseSerde = Serdes.String();

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
        topologyBuilder.addSource(command, stringSerde.deserializer(), commandDeserializer, taskCommandsTopic)
                .addProcessor(commandProcessor, new CommandProcessorSupplier(etagStoreName,
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
        System.out.println(topologyBuilder.describe());
    }
}
