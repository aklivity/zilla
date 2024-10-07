/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.risingwave.internal.config.RisingwaveCommandType;

public final class RisingwaveSqlCommandParser
{
    private static final String SQL_FUNCTION_PATTERN =
        "CREATE\\s+FUNCTION[\\s\\S]+?\\$\\$[\\s\\S]+?\\$\\$";
    private static final String SQL_COMMAND_PATTERN =
        "(?i)\\b(CREATE FUNCTION)\\b.*?\\$\\$(.*?)\\$\\$\\s*;[\\x00\\n]*" +
        "|\\b(CREATE FUNCTION)\\b.*?RETURNS .*?AS.*?;[\\x00\\n]*" +
        "|\\b(CREATE MATERIALIZED VIEW|CREATE SOURCE|CREATE SINK|CREATE INDEX|CREATE STREAM" +
            "|CREATE VIEW|SHOW TABLES|DESCRIBE|SHOW)\\b.*?;[\\x00\\n]*" +
        "|\\b(SELECT|INSERT|UPDATE|DELETE|ALTER|DROP|CREATE TABLE|CREATE SCHEMA|CREATE DATABASE)\\b.*?;[\\x00\\n]*";

    private final Pattern functionPattern = Pattern.compile(SQL_FUNCTION_PATTERN, Pattern.CASE_INSENSITIVE);
    private final Pattern sqlPattern = Pattern.compile(SQL_COMMAND_PATTERN, Pattern.DOTALL);

    public RisingwaveSqlCommandParser()
    {
    }

    public String detectFirstSQLCommand(
        String input)
    {
        String command = null;
        Matcher matcher = sqlPattern.matcher(input);

        if (matcher.find())
        {
            command = matcher.group();
        }

        return command;
    }

    public RisingwaveCommandType decodeCommandType(
        String input)
    {
        RisingwaveCommandType matchingCommand = RisingwaveCommandType.UNKNOWN_COMMAND;

        command:
        for (RisingwaveCommandType command : RisingwaveCommandType.values())
        {
            byte[] strBytes = input.getBytes(StandardCharsets.UTF_8);
            byte[] prefixBytes = command.value();

            boolean match = strBytes.length > prefixBytes.length;

            if (match)
            {
                for (int i = 0; i < prefixBytes.length; i++)
                {
                    if (strBytes[i] != prefixBytes[i])
                    {
                        match = false;
                        break;
                    }
                }
            }

            if (match)
            {
                matchingCommand = command;
                break command;
            }
        }

        if (matchingCommand == RisingwaveCommandType.CREATE_FUNCTION_COMMAND &&
            isEmbeddedFunction(input))
        {
            matchingCommand = RisingwaveCommandType.UNKNOWN_COMMAND;
        }


        return matchingCommand;
    }

    private boolean isEmbeddedFunction(
        String sqlQuery)
    {
        Matcher matcher = functionPattern.matcher(sqlQuery);
        return matcher.find();
    }
}
