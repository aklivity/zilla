/*
 * Copyright 2021-2022 Aklivity. All rights reserved.
 */
package io.aklivity.example.cqrs.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Builder
@Getter
public class DeleteTaskCommand implements Command
{
}
