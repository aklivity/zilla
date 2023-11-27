package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.Comparator;

public class HttpKafkaProduceHashComparator implements Comparator<byte[]>
{
    public int compare(
        byte[] array1,
        byte[] array2)
    {
        int minLength = Math.min(array1.length, array2.length);
        int result = 0;
        for (int i = 0; i < minLength && result == 0; i++)
        {
            result = Byte.compare(array1[i], array2[i]);
        }
        return result != 0 ? result : Integer.compare(array1.length, array2.length);
    }
}
