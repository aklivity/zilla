package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZview;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.String32FW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZviewMacro
{
    protected static final int FLAGS_INIT = 0x02;

    private static final String MATERIALIZED_VIEW_NAME = "MATERIALIZED VIEW";
    private static final String ZVIEW_NAME = "zviews";

    private final String32FW columnRO = new String32FW(ByteOrder.BIG_ENDIAN);

    private final List<String> columnTypes;
    private final List<String> columnDescriptions;
    private final Map<String, String> columns;
    private final String bootstrapServer;
    private final String schemaRegistry;
    private final String systemSchema;
    private final String user;
    private final String sql;
    private final CreateZview command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveCreateZviewMacro(
        String bootstrapServer,
        String schemaRegistry,
        String systemSchema,
        String user,
        String sql,
        CreateZview command,
        RisingwaveMacroHandler handler)
    {
        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
        this.systemSchema = systemSchema;
        this.user = user;
        this.sql = sql;
        this.command = command;
        this.handler = handler;

        this.columnTypes = new ArrayList<>();
        this.columnDescriptions = new ArrayList<>();
        this.columns = new Object2ObjectHashMap<>();
    }

    public RisingwaveMacroState start(
        long traceId,
        long authorization)
    {
        CreateMaterializedViewState state = new CreateMaterializedViewState();
        state.doExecute(traceId, authorization);

        return state;
    }

    private class CreateMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS %s AS %s;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String name = command.name();
            String select = command.select();

            String sqlQuery = String.format(sqlFormat, name, select);

            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            GrantResourceState state = new GrantResourceState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class GrantResourceState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            GRANT ALL PRIVILEGES ON %s %s.%s TO %s;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, MATERIALIZED_VIEW_NAME, command.schema(), command.name(), user);

            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DescribeMaterializedViewState state = new DescribeMaterializedViewState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DescribeMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DESCRIBE %s.%s;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.schema(), command.name());

            handler.doExecute(traceId, authorization, sqlQuery);
        }

         @Override
        public RisingwaveMacroState onType(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            columnTypes.clear();
            flushEx.type().columns()
                .forEach(c ->
                {
                    String name = c.name().asString();
                    name = name.substring(0, name.length() - 1);
                    columnTypes.add(name);
                });

            return this;
        }

        @Override
        public RisingwaveMacroState onRow(
            long traceId,
            long authorization,
            long routedId,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            OctetsFW extension)
        {
            int progress = offset;

            if ((flags & FLAGS_INIT) != 0x00)
            {
                columnDescriptions.clear();
                progress += Short.BYTES;
            }

            column:
            while (progress < limit)
            {
                String32FW column = columnRO.tryWrap(buffer, progress, limit);

                if (column == null)
                {
                    break column;
                }

                columnDescriptions.add(column.asString());

                progress = column.limit();
            }

            int nameIndex = columnTypes.indexOf("Name");
            int typeIndex = columnTypes.indexOf("Type");
            int isHiddenIndex = columnTypes.indexOf("Is Hidden");

            if ("false".equals(columnDescriptions.get(isHiddenIndex)))
            {
                columns.put(columnDescriptions.get(nameIndex), columnDescriptions.get(typeIndex));
            }

            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateTopicState state = new CreateTopicState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class CreateTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TOPIC IF NOT EXISTS %s (%s%s);\u0000""";
        private final String primaryKeyFormat = ", PRIMARY KEY (%s)";

        private final StringBuilder fieldBuilder = new StringBuilder();
        private final StringBuilder primaryKeyBuilder = new StringBuilder();


        private void doExecute(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            primaryKeyBuilder.setLength(0);
            columns.keySet().forEach(k -> primaryKeyBuilder.append(k).append(", "));
            primaryKeyBuilder.delete(primaryKeyBuilder.length() - 2, primaryKeyBuilder.length());

            String primaryKey = String.format(primaryKeyFormat, primaryKeyBuilder);

            fieldBuilder.setLength(0);

            columns.forEach((k, v) -> fieldBuilder.append(String.format("%s %s, ", k,
                RisingwavePgsqlTypeMapping.typeName(v))));
            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

            String sqlQuery = String.format(sqlFormat, topic, fieldBuilder, primaryKey);

            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateSinkState state = new CreateSinkState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class CreateSinkState implements RisingwaveMacroState
    {
        private final String sqlKafkaFormat = """
            CREATE SINK %s.%s_sink
            FROM %s
            WITH (
               connector='kafka',
               properties.bootstrap.server='%s',
               topic='%s.%s'%s
            ) FORMAT UPSERT ENCODE AVRO (
               schema.registry='%s'
            ) KEY ENCODE TEXT;\u0000""";

        private final StringBuilder fieldBuilder = new StringBuilder();
        private final StringBuilder primaryKeyBuilder = new StringBuilder();


        private void doExecute(
            long traceId,
            long authorization)
        {
            String topicSchema = command.schema();
            String viewName = command.name();

            Optional<Map.Entry<String, String>> primaryKeyMatch = columns.entrySet().stream()
                .filter(e -> "id".equalsIgnoreCase(e.getKey()))
                .findFirst();

            if (primaryKeyMatch.isEmpty())
            {
                primaryKeyMatch = columns.entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase().contains("id"))
                    .findFirst();
            }

            String textPrimaryKey = primaryKeyMatch.map(Map.Entry::getKey).orElse(null);
            String primaryKey = textPrimaryKey != null ? ",\n   primary_key='%s'".formatted(textPrimaryKey) : "";

            String sqlQuery = String.format(sqlKafkaFormat, systemSchema, viewName, viewName, bootstrapServer,
                topicSchema, viewName, primaryKey, schemaRegistry);

            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            InsertIntoCatalogState state = new InsertIntoCatalogState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class InsertIntoCatalogState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            INSERT INTO %s.%s (name, sql) VALUES ('%s', '%s');\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String name = command.name();
            String schema = command.schema();

            String newSql = sql.replace(ZVIEW_NAME, MATERIALIZED_VIEW_NAME)
                .replace("\u0000", "");
            String sqlQuery = String.format(sqlFormat, systemSchema, schema, name, newSql);

            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_ZVIEW_COMMAND);
            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doReady(traceId, authorization, sql.length());
            return this;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

}
