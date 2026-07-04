package com.player.infrastructure.audio;

import com.player.core.ports.AudioPlayerInterface;
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
    private SourceDataLine lineaAudioActual; 
    private String rutaArchivoActual;
    private boolean enPausa = false;
    private float volumenActual = 1.0f; 
    private boolean aplicarModoMinion = false;
    private long totalBytesArchivo = 0;
    private long bytesSaltadosActuales = 0;

    @Override
    public void play(String rutaArchivo, double speed, boolean modoMinion) {
        stop(); 
        this.rutaArchivoActual = rutaArchivo;
        this.aplicarModoMinion = modoMinion;
        this.enPausa = false;
        this.bytesSaltadosActuales = 0;
        ejecutarHilo(bytesSaltadosActuales);
    }

    @Override
    public void seek(double porcentaje) {
        if (rutaArchivoActual == null || totalBytesArchivo <= 0) return;
        
        long nuevosBytes = (long) (totalBytesArchivo * (porcentaje / 100.0));
        // Alinear a tramas estéreo de 16 bits (4 bytes)
        this.bytesSaltadosActuales = (nuevosBytes / 4) * 4; 
        
        boolean estabaEnPausa = this.enPausa;
        
        stopHilosYLineas();
        this.enPausa = estabaEnPausa;
        ejecutarHilo(this.bytesSaltadosActuales);
    }

    private void ejecutarHilo(final long bytesAEnviar) {
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

                this.totalBytesArchivo = archivoFisico.length();

                if (rutaAbsoluta.toLowerCase().endsWith(".wav")) {
                    reproducirComoWav(archivoFisico, bytesAEnviar);
                    return;
                }

                // 🎵 MP3 PROCESADO DINÁMICAMENTE POR CUADROS
                try (FileInputStream fis = new FileInputStream(archivoFisico)) {
                    if (bytesAEnviar > 0) {
                        fis.skip(bytesAEnviar);
                    }

                    javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(fis);
                    javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
                    
                    javazoom.jl.decoder.Header header = bitstream.readFrame();
                    if (header == null) {
                        bitstream.close();
                        return;
                    }

                    // 🔥 DETECCIÓN DINÁMICA: Obtenemos los Hz reales nativos del archivo de música
                    float sampleRateNativo = header.frequency(); 
                    
                    // Si el modo minion está activo lo aceleramos un 50%, si no, se queda en su ritmo original
                    float sampleRateFinal = sampleRateNativo * (this.aplicarModoMinion ? 1.5f : 1.0f); 
                    
                    AudioFormat formatoSalida = new AudioFormat(sampleRateFinal, 16, 2, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, formatoSalida);
                    
                    synchronized (this) {
                        lineaAudioActual = (SourceDataLine) AudioSystem.getLine(info);
                        lineaAudioActual.open(formatoSalida);
                    }
                    
                    actualizarVolumenLinea();
                    lineaAudioActual.start();
                    
                    // Procesamos el primer cuadro que ya leímos para calcular los Hz
                    do {
                        // Control de Pausa real y sincronizado
                        while (enPausa && !Thread.currentThread().isInterrupted()) {
                            Thread.sleep(50);
                        }
                        
                        if (Thread.currentThread().isInterrupted()) break;
                        
                        javazoom.jl.decoder.SampleBuffer output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                        short[] samples = output.getBuffer();
                        int longitudBuffer = output.getBufferLength();
                        byte[] byteBuffer = new byte[longitudBuffer * 2];
                        
                        for (int i = 0; i < longitudBuffer; i++) {
                            byteBuffer[i * 2] = (byte) (samples[i] & 0xff);
                            byteBuffer[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xff);
                        }
                        
                        lineaAudioActual.write(byteBuffer, 0, byteBuffer.length);
                        bitstream.closeFrame();
                        
                    } while ((header = bitstream.readFrame()) != null && !Thread.currentThread().isInterrupted());
                    
                    if (lineaAudioActual != null && !Thread.currentThread().isInterrupted()) {
                        lineaAudioActual.drain();
                    }
                    bitstream.close();
                    
                } catch (Exception e) {
                    System.out.println("🔄 Cambiando a modo compatibilidad WAV...");
                    reproducirComoWav(archivoFisico, bytesAEnviar);
                }

            } catch (Exception e) {
                System.err.println("Error crítico en reproducción: " + e.getMessage());
            }
        });
        hiloTrabajador.start();
    }

    private void reproducirComoWav(File archivo, long bytesAEnviar) {
        try (AudioInputStream aisBase = AudioSystem.getAudioInputStream(archivo)) {
            if (bytesAEnviar > 0) {
                aisBase.skip(bytesAEnviar);
            }

            AudioFormat formatBase = aisBase.getFormat();
            float sampleRateFinal = formatBase.getSampleRate() <= 0 ? 44100.0f : formatBase.getSampleRate();
            
            if (this.aplicarModoMinion) {
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
                    while (enPausa && !Thread.currentThread().isInterrupted()) {
                        Thread.sleep(50);
                    }
                    if (Thread.currentThread().isInterrupted()) break;
                    lineaAudioActual.write(buffer, 0, bytesLeidos);
                }
                if (lineaAudioActual != null && !Thread.currentThread().isInterrupted()) {
                    lineaAudioActual.drain();
                }
            }
        } catch (Exception ex) {
            System.err.println("Error en decodificador WAV: " + ex.getMessage());
        }
    }

    @Override
    public void pause() {
        enPausa = true;
        if (lineaAudioActual != null) {
            lineaAudioActual.stop(); // Congela el flujo de hardware de forma inmediata
        }
    }

    @Override
    public void resume() {
        if (enPausa) {
            enPausa = false;
            if (lineaAudioActual != null) {
                lineaAudioActual.start(); // Reanuda la misma línea de datos sin perder la posición
            }
        }
    }

    private void stopHilosYLineas() {
        synchronized (this) {
            if (lineaAudioActual != null) {
                try {
                    lineaAudioActual.stop();
                    lineaAudioActual.flush();
                    lineaAudioActual.close();
                } catch(Exception e){}
                lineaAudioActual = null;
            }
        }
        if (hiloTrabajador != null && hiloTrabajador.isAlive()) {
            hiloTrabajador.interrupt();
            try {
                hiloTrabajador.join(200); // Dar tiempo seguro al hilo para cerrarse
            } catch (Exception e) {}
        }
    }

    @Override
    public void stop() {
        enPausa = false;
        bytesSaltadosActuales = 0;
        stopHilosYLineas();
    }

    @Override
    public void setSpeed(double speed) {}

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
        } catch (Exception e) {}
    }
}