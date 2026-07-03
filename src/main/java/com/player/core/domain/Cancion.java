package com.player.core.domain;

public class Cancion {
    private String id;
    private String titulo;
    private String artista;
    private String rutaArchivo; 
    private String urlPortada;  
    private String idioma;      
    private String tonoEfecto;  

    // Constructor
    public Cancion(String id, String titulo, String artista, String rutaArchivo, String urlPortada, String idioma, String tonoEfecto) {
        this.id = id;
        this.titulo = titulo;
        this.artista = artista;
        this.rutaArchivo = rutaArchivo;
        this.urlPortada = urlPortada;
        this.idioma = idioma;
        this.tonoEfecto = tonoEfecto;
    }

    // Getters y Setters
    public String getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getArtista() { return artista; }
    public String getRutaArchivo() { return rutaArchivo; }
    public String getUrlPortada() { return urlPortada; }
    public String getIdioma() { return idioma; }
    public String getTonoEfecto() { return tonoEfecto; }
}
