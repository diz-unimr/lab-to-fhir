package de.unimarburg.diz.labtofhir.model;

public class MappingContainer<K, V> {

    private final K source;
    private V value;
    private MappingResult resultType = MappingResult.SUCCESS;
    private Exception exception;

    public MappingContainer(K source, V value) {
        this.source = source;
        this.value = value;
    }

    public V getValue() {
        return value;
    }

    public MappingContainer<K, V> setValue(V value) {
        this.value = value;
        return this;
    }

    public MappingResult getResultType() {
        return resultType;
    }

    public MappingContainer<K, V> withResultType(MappingResult resultType) {
        this.resultType = resultType;
        return this;
    }

    public K getSource() {
        return source;
    }

    public Exception getException() {
        return exception;
    }

    public MappingContainer<K, V> withException(Exception exception) {
        this.exception = exception;
        withResultType(MappingResult.EXCEPTION);
        return this;
    }

}
