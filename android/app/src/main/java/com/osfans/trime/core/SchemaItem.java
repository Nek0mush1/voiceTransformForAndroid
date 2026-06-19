package com.osfans.trime.core;

public final class SchemaItem {
    public final String id;
    public final String name;

    public SchemaItem(String id, String name) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
    }
}
