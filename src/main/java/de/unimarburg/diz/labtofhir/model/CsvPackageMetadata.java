package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;
import java.util.List;

public class CsvPackageMetadata {

    private String profile;
    private String name;
    private String title;
    private String version;
    private String gitCommit;
    private Instant created;
    private String checksum;
    private List<MetadataResource> resources;

    public String getProfile() {
        return profile;
    }

    public CsvPackageMetadata setProfile(String profile) {
        this.profile = profile;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public CsvPackageMetadata setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    @JsonSetter("git-commit")
    public CsvPackageMetadata setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
        return this;
    }

    public List<MetadataResource> getResources() {
        return resources;
    }

    @JsonSetter("resources")
    public CsvPackageMetadata setResources(List<MetadataResource> resources) {
        this.resources = resources;
        return this;
    }

    public Instant getCreated() {
        return created;
    }

    public CsvPackageMetadata setCreated(Instant created) {
        this.created = created;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public CsvPackageMetadata setVersion(String version) {
        this.version = version;
        return this;
    }

    /*
     * SHA-256 checksum
     */
    public String getChecksum() {
        return checksum;
    }

    public CsvPackageMetadata setChecksum(String checksum) {
        this.checksum = checksum;
        return this;
    }

    public String getName() {
        return name;
    }

    public CsvPackageMetadata setName(String name) {
        this.name = name;
        return this;
    }


}
