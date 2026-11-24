package gestor.persistencia;

import gestor.excepcion.GestorException.ErrorPersistencia;
import gestor.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/** Persistencia local en JSON (RF-19, RNF-04). */
public class RepositorioFinanciero {
    private final Path ruta;

    public RepositorioFinanciero(Path ruta) { this.ruta = ruta; }

    /** Devuelve el estado guardado, o vacío si aún no existe el archivo. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> cargar() {
        if (!Files.exists(ruta)) return Optional.empty();
        try {
            Object datos = Json.leer(Files.readString(ruta, StandardCharsets.UTF_8));
            if (!(datos instanceof Map)) throw new IllegalArgumentException("se esperaba un objeto JSON");
            return Optional.of((Map<String, Object>) datos);
        } catch (IOException | IllegalArgumentException e) {
            throw new ErrorPersistencia("No se pudo leer " + ruta + ": " + e.getMessage(), e);
        }
    }

    /** Escribe en un temporal y lo reemplaza, para no dañar datos válidos si algo falla. */
    public void guardar(Map<String, Object> estado) {
        Path temporal = ruta.resolveSibling(ruta.getFileName() + ".tmp");
        try {
            if (ruta.getParent() != null) Files.createDirectories(ruta.getParent());
            Files.writeString(temporal, Json.escribir(estado), StandardCharsets.UTF_8);
            try {
                Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ErrorPersistencia("No se pudo guardar en " + ruta + ": " + e.getMessage(), e);
        }
    }

    public Path respaldar() {
        if (!Files.exists(ruta)) throw new ErrorPersistencia("No hay datos guardados para respaldar.");
        String nombre = ruta.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        String base = punto < 0 ? nombre : nombre.substring(0, punto);
        String extension = punto < 0 ? "" : nombre.substring(punto);
        String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path destino = ruta.resolveSibling("respaldos").resolve(base + "_" + marca + extension);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(ruta, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ErrorPersistencia("No se pudo crear el respaldo: " + e.getMessage(), e);
        }
        return destino;
    }
}
