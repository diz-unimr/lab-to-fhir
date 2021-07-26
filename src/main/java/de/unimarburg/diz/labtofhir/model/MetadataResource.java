package de.unimarburg.diz.labtofhir.model;

public class MetadataResource {

    private String path;
    private String profile;
    private String name;
    private String format;
    private String mediatype;
    private String encoding;
    private MetadataResourceSchema schema;

    public String getPath() {
        return path;
    }

    public MetadataResource setPath(String path) {
        this.path = path;
        return this;
    }

    public String getProfile() {
        return profile;
    }

    public MetadataResource setProfile(String profile) {
        this.profile = profile;
        return this;
    }

    public String getName() {
        return name;
    }

    public MetadataResource setName(String name) {
        this.name = name;
        return this;
    }

    public String getFormat() {
        return format;
    }

    public MetadataResource setFormat(String format) {
        this.format = format;
        return this;
    }

    public String getMediatype() {
        return mediatype;
    }

    public MetadataResource setMediatype(String mediatype) {
        this.mediatype = mediatype;
        return this;
    }

    public String getEncoding() {
        return encoding;
    }

    public MetadataResource setEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    public MetadataResourceSchema getSchema() {
        return schema;
    }

    public MetadataResource setSchema(
        MetadataResourceSchema schema) {
        this.schema = schema;
        return this;
    }
}
