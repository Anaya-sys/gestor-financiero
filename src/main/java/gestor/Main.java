package gestor;

import gestor.consola.Consola;
import gestor.excepcion.GestorException.ErrorPersistencia;
import gestor.observador.ObservadorBitacora;
import gestor.observador.ObservadorPresupuesto;
import gestor.observador.ObservadorVencimientos;
import gestor.persistencia.RepositorioFinanciero;
import gestor.servicio.GestorFinanciero;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Punto de entrada: ensambla el gestor, suscribe los observadores y lanza la consola. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Path datos = Paths.get(System.getProperty("user.dir"), "datos");
        GestorFinanciero gestor;
        try {
            gestor = new GestorFinanciero(new RepositorioFinanciero(datos.resolve("gestor.json")));
        } catch (ErrorPersistencia e) {
            System.out.println("[x] " + e.getMessage());
            System.out.println("Corrija o mueva el archivo para continuar; no se sobrescribió nada.");
            System.exit(1);
            return;
        }
        ObservadorPresupuesto obsPresupuesto = new ObservadorPresupuesto(gestor, System.out::println);
        gestor.suscribir(obsPresupuesto);
        gestor.suscribir(new ObservadorVencimientos(3, System.out::println));
        gestor.suscribir(new ObservadorBitacora(datos.resolve("bitacora.log")));

        // En una terminal, usar su charset (en Windows suele diferir del de la JVM) para leer bien las tildes.
        Charset charset = System.console() != null ? System.console().charset() : Charset.defaultCharset();
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charset));
        new Consola(gestor, obsPresupuesto, prompt -> {
            System.out.print(prompt);
            System.out.flush();
            try {
                return teclado.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, System.out::println).ejecutar();
    }
}
