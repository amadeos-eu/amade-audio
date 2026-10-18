package eu.amadeos.audio.domain;

/**
 * An audio output of the sound server, e.g. speakers, headphones or HDMI.
 *
 * @param name technical sink name
 */
public record OutputDevice(String name, String description, boolean isDefault) {
    public OutputDevice {
        description = description == null || description.isBlank() ? name : description.strip();
    }
}
