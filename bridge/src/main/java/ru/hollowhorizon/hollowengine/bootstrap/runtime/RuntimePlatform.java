package ru.hollowhorizon.hollowengine.bootstrap.runtime;

public enum RuntimePlatform {
    FABRIC("fabric"),
    NEOFORGE("neoforge");

    private final String id;

    RuntimePlatform(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
