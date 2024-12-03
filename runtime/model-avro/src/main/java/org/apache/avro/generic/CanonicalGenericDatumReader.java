/*
 * Copyright 2021-2024 Aklivity Inc
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
package org.apache.avro.generic;

import org.apache.avro.Schema;
import org.apache.avro.io.ResolvingDecoder;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Specialization of the AVRO GenericDatumReader that implements the DefaultValueProvider interface
 * It basically just tracks which field is being read and can then return the schema default for that
 * field if the decoder needs it. We also decode unions of null and a singletype as a more normal key=value
 * rather than key={type=value}.
 *
 * @param <T>
 */
public class CanonicalGenericDatumReader<T> extends GenericDatumReader<T> implements DefaultValueProvider
{

    public CanonicalGenericDatumReader(final Schema classSchema)
    {
        super(classSchema);
    }

    public CanonicalGenericDatumReader(final Schema writer, final Schema reader)
    {
        super(writer, reader);
    }

    public CanonicalGenericDatumReader(final Schema writer, final Schema reader, final GenericData data)
    {
        super(writer, reader, data);
    }

    public CanonicalGenericDatumReader(final GenericData data)
    {
        super(data);
    }

    private Schema.Field currentField = null;

    @Override
    public final JsonNode getCurrentFieldDefault()
    {
        return currentField.defaultValue();
    }

    /**
     * Overwritten to allow access to the current processed field default value from the JsonDecoder.
     *
     * @param r
     * @param f
     * @param oldDatum
     * @param in
     * @param state
     * @throws IOException
     */
    //CHECKSTYLE IGNORE DesignForExtension FOR NEXT 30 LINES
    @Override
    protected void readField(final Object r, final Schema.Field f, final Object oldDatum,
                             final ResolvingDecoder in, final Object state) throws IOException
    {
        currentField = f;
        super.readField(r, f, oldDatum, in, state);
    }

}
