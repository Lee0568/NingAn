package net.thekingofduck.loki.controller;

public class AutoBanSettingsDto {
    private int frequency;
    private int duration;
    // Getters and Setters
    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
}
