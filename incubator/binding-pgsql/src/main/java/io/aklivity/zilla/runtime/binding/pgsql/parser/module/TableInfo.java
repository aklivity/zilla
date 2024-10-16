package io.aklivity.zilla.runtime.binding.pgsql.parser.module;

import java.util.Map;
import java.util.Set;

public record TableInfo(
        String tableName,
        Map<String, String> columns,
        Set<String> primaryKeys)
{
}
