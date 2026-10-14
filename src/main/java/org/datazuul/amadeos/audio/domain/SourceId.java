package org.datazuul.amadeos.audio.domain;

/** The devices (racks) that can play audio. */
public enum SourceId {
    CD("CD"),
    PLAYER("Player"),
    WEBRADIO("Web Radio");

    private final String label;

    SourceId(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
