// File: src/main/java/com/build4all/feeders/importer/ReplaceScope.java
package com.build4all.importer.model;

public enum ReplaceScope {
    TENANT, // safe: tenant-scoped only
    FULL    // also deletes project-scoped categories/itemTypes (use carefully)
}
