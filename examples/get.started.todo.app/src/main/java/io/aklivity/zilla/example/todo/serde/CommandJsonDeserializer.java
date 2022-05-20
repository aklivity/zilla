/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.zilla.example.todo.serde;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.aklivity.zilla.example.todo.model.Command;
import io.aklivity.zilla.example.todo.model.CreateTaskCommand;
import io.aklivity.zilla.example.todo.model.DeleteTaskCommand;
import io.aklivity.zilla.example.todo.model.RenameTaskCommand;
import io.confluent.kafka.serializers.jackson.Jackson;

public class CommandJsonDeserializer implements Deserializer<Command>
{
    private ObjectMapper objectMapper;
    public CommandJsonDeserializer()
    {
        this.objectMapper = Jackson.newObjectMapper();
    }

    @Override
    public Command deserialize(String topic, Headers headers, byte[] data)
    {
        final Header domainModelHeader = headers.lastHeader("zilla:domain-model");
        final Header correlationId = headers.lastHeader("zilla:correlation-id");
        final String domainModel = new String(domainModelHeader.value());

        if (correlationId != null)
        {
            JavaType type = null;
            switch (domainModel)
            {
            case "CreateTaskCommand" :
                type = objectMapper.getTypeFactory().constructType(CreateTaskCommand.class);
                break;
            case "RenameTaskCommand" :
                type = objectMapper.getTypeFactory().constructType(RenameTaskCommand.class);
                break;
            case "DeleteTaskCommand" :
                type = objectMapper.getTypeFactory().constructType(DeleteTaskCommand.class);
                break;
            }
            return deserialize(data, type);
        }
        else
        {
            throw new IllegalArgumentException("Missing correlation-id header");
        }
    }

    @Override
    public Command deserialize(String s, byte[] bytes)
    {
        return null;
    }

    Command deserialize(byte[] bytes, JavaType type)
    {
        if (bytes != null && bytes.length != 0)
        {
            try
            {
                return this.objectMapper.readValue(bytes, type);
            }
            catch (Exception var4)
            {
                throw new SerializationException(var4);
            }
        }
        else
        {
            return new DeleteTaskCommand();
        }
    }
}
