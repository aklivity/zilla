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

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;

public abstract class RisingwaveCommandTemplate
{
    private final CCJSqlParserManager parserManager = new CCJSqlParserManager();
    private final Map<String, String> includeMap = new Object2ObjectHashMap<>();

    protected final StringBuilder includeBuilder = new StringBuilder();
    protected static final Map<String, String> ZILLA_MAPPINGS = new Object2ObjectHashMap<>();

    static
    {
        ZILLA_MAPPINGS.put("zilla_correlation_id", "INCLUDE header 'zilla:correlation-id' AS %s\n");
        ZILLA_MAPPINGS.put("zilla_identity", "INCLUDE header 'zilla:identity' AS %s\n");
        ZILLA_MAPPINGS.put("timestamp", "INCLUDE timestamp AS %s\n");
    }

    public String primaryKey(
        CreateTable statement)
    {
        String primaryKey = null;

        final List<Index> indexes = statement.getIndexes();

        if (indexes != null && !indexes.isEmpty())
        {
            match:
            for (Index index : indexes)
            {
                if ("PRIMARY KEY".equalsIgnoreCase(index.getType()))
                {
                    final List<Index.ColumnParams> primaryKeyColumns = index.getColumns();
                    primaryKey = primaryKeyColumns.get(0).columnName;
                    break match;
                }
            }
        }

        return primaryKey;
    }

    public RisingwaveCreateTableCommand parserCreateTable(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        String query = buffer.getStringWithoutLengthUtf8(offset, length);

        int includeIndex = query.indexOf("INCLUDE");

        String createTablePart;
        String includePart = null;

        CreateTable createTable = null;
        Map<String, String> includes = null;

        if (includeIndex != -1)
        {
            createTablePart = query.substring(0, includeIndex).trim();
            includePart = query.substring(includeIndex).trim();
        }
        else
        {
            createTablePart = query.trim();
        }

        try
        {
            createTable = (CreateTable) parserManager.parse(new StringReader(createTablePart));
        }
        catch (Exception ignore)
        {
        }

        if (includePart != null)
        {
            includes = parseSpecificIncludes(includePart);
        }

        return new RisingwaveCreateTableCommand(createTable, includes);
    }

    private Map<String, String> parseSpecificIncludes(
        String includePart)
    {
        String[] includeClauses = includePart.toLowerCase().split("include");
        for (String clause : includeClauses)
        {
            clause = clause.trim();
            if (!clause.isEmpty())
            {
                String[] parts = clause.toLowerCase().split("as");
                if (parts.length == 2)
                {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replace(";", "");

                    if (isValidInclude(key))
                    {
                        includeMap.put(key, value);
                    }
                }
            }
        }

        return includeMap;
    }

    private static boolean isValidInclude(
        String key)
    {
        return "zilla_correlation_id".equals(key) ||
               "zilla_identity".equals(key) ||
               "timestamp".equals(key);
    }
}
