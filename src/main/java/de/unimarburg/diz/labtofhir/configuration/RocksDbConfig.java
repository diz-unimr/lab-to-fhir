package de.unimarburg.diz.labtofhir.configuration;

import java.util.Map;
import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.CompactionStyle;
import org.rocksdb.Options;

public class RocksDbConfig implements RocksDBConfigSetter {

    @Override
    public void setConfig(final String storeName, final Options options,
        final Map<String, Object> configs) {
        options.setCompactionStyle(CompactionStyle.LEVEL);
    }

    @Override
    public void close(final String storeName, final Options options) {
    }

}


