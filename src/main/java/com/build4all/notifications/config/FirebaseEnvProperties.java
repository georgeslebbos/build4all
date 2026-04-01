package com.build4all.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseEnvProperties {

    private EnvConfig test = new EnvConfig();
    private EnvConfig prod = new EnvConfig();
    private EnvConfig dev = new EnvConfig();

    public EnvConfig getTest() {
        return test;
    }

    public void setTest(EnvConfig test) {
        this.test = test;
    }

    public EnvConfig getProd() {
        return prod;
    }

    public void setProd(EnvConfig prod) {
        this.prod = prod;
    }

    public EnvConfig getDev() {
        return dev;
    }

    public void setDev(EnvConfig dev) {
        this.dev = dev;
    }

    public static class EnvConfig {
        private String projectId;
        private String projectName;
        private String serviceAccountSecretRef;

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getServiceAccountSecretRef() {
            return serviceAccountSecretRef;
        }

        public void setServiceAccountSecretRef(String serviceAccountSecretRef) {
            this.serviceAccountSecretRef = serviceAccountSecretRef;
        }
    }
}