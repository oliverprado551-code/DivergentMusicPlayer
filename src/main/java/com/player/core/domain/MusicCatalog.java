package com.player.core.domain;

public class MusicCatalog {
    // La lista de canciones ahora vive en el dominio, separada de la interfaz gráfica
    private static final String[] CATALOGO = {
        "despacito_espanol.mp3", "despacito_ingles.mp3", "happy_espanol.mp3", 
        "happy_ingles.mp3", "letitgo_ingles.mp3", "libresoy_espanol.mp3", 
        "subeme_espanol.mp3", "subeme_ingles.mp3", "waka_espanol.mp3", "waka_ingles.mp3"
    };

    public static String[] obtenerTodas() {
        return CATALOGO;
    }
}