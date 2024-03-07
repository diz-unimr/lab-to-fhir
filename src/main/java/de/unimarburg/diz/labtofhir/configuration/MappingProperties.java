package de.unimarburg.diz.labtofhir.configuration;

import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mapping")
public class MappingProperties implements Serializable {

    private final Loinc loinc = new Loinc();

    public Loinc getLoinc() {
        return loinc;
    }

    public static class Loinc implements Serializable {

        private final Credentials credentials = new Credentials();
        private String version;
        private String proxy;
        private String local;

        public Credentials getCredentials() {
            return credentials;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getProxy() {
            return proxy;
        }

        public void setProxy(String proxy) {
            this.proxy = proxy;
        }

        public String getLocal() {
            return local;
        }

        public void setLocal(String local) {
            this.local = local;
        }

        public static class Credentials implements Serializable {

            private String user;
            private String password;

            public String getUser() {
                return user;
            }

            public void setUser(String user) {
                this.user = user;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }
        }
    }

}
