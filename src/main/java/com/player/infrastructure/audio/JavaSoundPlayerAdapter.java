package com.player.infrastructure.audio;

import com.player.core.ports.AudioPlayerInterface;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine;
import java.io.File;
import java.io.FileInputStream;

public class JavaSoundPlayerAdapter implements AudioPlayerInterface {
    private Thread hiloTrabajador;
    private AdvancedPlayer reproductorMp3;
    private SourceDataLine lineaAudioActual; // Control para inyectar bytes PCM acelerados
    private int framePausa = 0; 
    private String rutaArchivoActual;
    private boolean enPausa = false;
    private float volumenActual = 1.0f; 
    private boolean aplicarModoMinion = false;

    @Override
    public void play(String rutaArchivo, double speed, boolean modoMinion) {
        stop(); // Limpia cualquier residuo de reproducción anterior
        this.rutaArchivoActual = rutaArchivo;
        this.aplicarModoMinion = modoMinion;
        this.enPausa = false;
        this.framePausa = 0;
        ejecutarHilo(0);
    }

    private void ejecutarHilo(final int frameInicio) {
        hiloTrabajador = new Thread(() -> {
            try {
                String rutaAbsoluta = rutaArchivoActual.startsWith("/") 
                        ? "C:/DivergentMusicPlayer/src/main/java" + rutaArchivoActual 
                        : rutaArchivoActual;

                File archivoFisico = new File(rutaAbsoluta);
                if (!archivoFisico.exists()) {
                    System.err.println("No se encontró el archivo: " + rutaAbsoluta);
                    return;
                }

                // Si es un archivo WAV nativo, se procesa directo por el decodificador de líneas
                if (rutaAbsoluta.toLowerCase().endsWith(".wav")) {
                    reproducirComoWav(archivoFisico);
                    return;
                }

                // SI ES MP3 (Manejo adaptativo normal y Modo Minion acelerado)
                try (FileInputStream fis = new FileInputStream(archivoFisico)) {
                    if (this.aplicarModoMinion) {
                        System.out.println("Applying Minion Effect (Native MP3 Decoding & Sampling Acceleration)...");
                        
                        // Decodificamos el MP3 usando JLayer pero extrayendo el audio a una línea de sonido acelerada
                        javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(fis);
                        javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
                        
                        // Forzamos un formato de salida de audio con hercios acelerados (Voz de ardilla)
                        float sampleRateAcelerado = 44100.0f * 1.5f; 
                        AudioFormat formatoMinion = new AudioFormat(sampleRateAcelerado, 16, 2, true, false);
                        
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, formatoMinion);
                        synchronized (this) {
                            lineaAudioActual = (SourceDataLine) AudioSystem.getLine(info);
                            lineaAudioActual.open(formatoMinion);
                        }
                        
                        actualizarVolumenLinea();
                        lineaAudioActual.start();
                        
                        javazoom.jl.decoder.Header header;
                        while ((header = bitstream.readFrame()) != null && !Thread.currentThread().isInterrupted()) {
                            while (enPausa) {
                                Thread.sleep(50); // Mantiene congelado el hilo de audio en pausa de forma segura
                            }
                            
                            javazoom.jl.decoder.SampleBuffer output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                            short[] samples = output.getBuffer();
                            
                            // Usamos el método correcto de JLayer
                            int longitudBuffer = output.getBufferLength();
                            byte[] byteBuffer = new byte[longitudBuffer * 2];
                            
                            for (int i = 0; i < longitudBuffer; i++) {
                                byteBuffer[i * 2] = (byte) (samples[i] & 0xff);
                                byteBuffer[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xff);
                            }
                            
                            lineaAudioActual.write(byteBuffer, 0, byteBuffer.length);
                            bitstream.closeFrame();
                        }
                        
                        if (lineaAudioActual != null) lineaAudioActual.drain();
                        bitstream.close();
                        
                    } else {
                        // MP3 Estándar sin efectos usando JLayer puro
                        reproductorMp3 = new AdvancedPlayer(fis);
                        if (frameInicio > 0) {
                            reproductorMp3.play(frameInicio, Integer.MAX_VALUE);
                        } else {
                            reproductorMp3.play();
                        }
                    }
                } catch (Exception e) {
                    System.out.println("🔄 Error en decodificación MP3, intentando modo rescate WAV...");
                    reproducirComoWav(archivoFisico);
                }

            } catch (java.lang.Exception e) {
                System.err.println("Error crítico en reproducción: " + e.getMessage());
            }
        });
        hiloTrabajador.start();
    }

    private void reproducirComoWav(File archivo) {
        try (AudioInputStream aisBase = AudioSystem.getAudioInputStream(archivo)) {
            AudioFormat formatBase = aisBase.getFormat();
            float sampleRateFinal = formatBase.getSampleRate() <= 0 ? 44100.0f : formatBase.getSampleRate();
            
            if (this.aplicarModoMinion) {
                System.out.println("Applying Minion Effect to WAV...");
                sampleRateFinal = sampleRateFinal * 1.5f; 
            }

            AudioFormat pcmCompatible = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, sampleRateFinal, 16,
                formatBase.getChannels() <= 0 ? 2 : formatBase.getChannels(),
                (formatBase.getChannels() <= 0 ? 2 : formatBase.getChannels()) * 2,
                sampleRateFinal, false
            );

            try (AudioInputStream ais = AudioSystem.getAudioInputStream(pcmCompatible, aisBase)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmCompatible);
                synchronized (this) {
                    lineaAudioActual = (SourceDataLine) AudioSystem.getLine(info);
                    lineaAudioActual.open(pcmCompatible);
                }
                
                actualizarVolumenLinea();
                lineaAudioActual.start();
                
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = ais.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                    while (enPausa) {
                        Thread.sleep(50);
                    }
                    lineaAudioActual.write(buffer, 0, bytesLeidos);
                }
                if (lineaAudioActual != null) lineaAudioActual.drain();
            }
        } catch (Exception ex) {
            System.err.println("Error en decodificador WAV alternativo: " + ex.getMessage());
        }
    }

    @Override
    public void pause() {
        enPausa = true;
        if (reproductorMp3 != null) {
            reproductorMp3.close();
            if (hiloTrabajador != null) hiloTrabajador.interrupt();
        } else if (lineaAudioActual != null) {
            lineaAudioActual.stop(); // Detiene el flujo de datos a la tarjeta sin destruir la posición
        }
    }

    @Override
    public void resume() {
        if (enPausa) {
            enPausa = false;
            if (lineaAudioActual != null) {
                lineaAudioActual.start(); // Reanuda la línea desde donde se congeló
            } else {
                ejecutarHilo(framePausa);
            }
        }
    }

    @Override
    public void stop() {
        enPausa = false;
        framePausa = 0;
        
        if (reproductorMp3 != null) {
            try { reproductorMp3.close(); } catch(Exception e){}
        }
        
        synchronized (this) {
            if (lineaAudioActual != null) {
                try {
                    lineaAudioActual.stop();
                    lineaAudioActual.close();
                } catch(Exception e){}
                lineaAudioActual = null;
            }
        }
        
        if (hiloTrabajador != null && hiloTrabajador.isAlive()) {
            hiloTrabajador.interrupt();
        }
    }

    @Override
    public void setSpeed(double speed) {
        // Controlado dinámicamente mediante el sample rate acelerado
    }

    @Override
    public void setVolume(float volumen) {
        this.volumenActual = volumen;
        actualizarVolumenLinea(); 
    }

    private void actualizarVolumenLinea() {
        try {
            if (lineaAudioActual != null && lineaAudioActual.isOpen()) {
                if (lineaAudioActual.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) lineaAudioActual.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log(volumenActual == 0 ? 0.0001 : volumenActual) / Math.log(10.0) * 20.0);
                    gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(dB, gainControl.getMaximum())));
                }
            }
        } catch (Exception e) {
            System.out.println("No se pudo aplicar el control de ganancia a la línea de audio.");
        }
    }
}