package com.player.core.ports;

public interface AudioPlayerInterface {
    void play(String rutaArchivo, double speed, boolean modoMinion);
    void pause();
    void resume();
    void stop();
    void setSpeed(double speed);
    void setVolume(float volumen);
    void seek(double porcentaje);
}