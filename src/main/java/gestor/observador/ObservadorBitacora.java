package gestor.observador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registra todos los eventos en memoria y, si se indica una ruta, en un archivo de texto. */
public class ObservadorBitacora implements ObservadorFinanciero {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path ruta;
    private final List<String> registros = new ArrayList<>();

    public ObservadorBitacora(Path ruta) { this.ruta = ruta; }

    public ObservadorBitacora() { this(null); }

    @Override
    public void actualizar(Evento evento) {
        String linea = LocalDateTime.now().format(FORMATO) + " | " + evento.tipo().descripcion() + " | " + evento.mensaje();
        registros.add(linea);
        if (ruta == null) return;
        try {
            if (ruta.getParent() != null) Files.createDirectories(ruta.getParent());
            Files.writeString(ruta, linea + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // la bitácora nunca debe interrumpir una operación financiera
        }
    }

    public List<String> registros() { return Collections.unmodifiableList(registros); }
}
