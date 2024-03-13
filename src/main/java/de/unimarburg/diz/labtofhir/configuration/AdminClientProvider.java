package de.unimarburg.diz.labtofhir.configuration;

import org.apache.kafka.clients.admin.Admin;

@FunctionalInterface
public interface AdminClientProvider {

    Admin createClient();
}
