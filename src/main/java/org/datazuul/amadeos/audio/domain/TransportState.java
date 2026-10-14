package org.datazuul.amadeos.audio.domain;

/** State of one device's transport. */
public enum TransportState {
    STOPPED,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR;

    /** True while the device produces or is about to produce sound. */
    public boolean isActive() {
        return this == LOADING || this == PLAYING || this == PAUSED;
    }
}
