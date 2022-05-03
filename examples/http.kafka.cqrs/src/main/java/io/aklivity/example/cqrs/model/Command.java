/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.example.cqrs.model;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = NAME, include = PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateTaskCommand.class, name = "CreateTaskCommand"),
    @JsonSubTypes.Type(value = UpdateTaskCommand.class, name = "UpdateTaskCommand"),
    @JsonSubTypes.Type(value = DeleteTaskCommand.class, name = "DeleteTaskCommand")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Command
{
}
