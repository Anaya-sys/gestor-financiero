package gestor.dominio;

import gestor.excepcion.GestorException.AbonoInvalido;
import gestor.excepcion.GestorException.CuentaSaldada;
import gestor.excepcion.GestorException.DatoInvalido;
import gestor.excepcion.GestorException.MontoInvalido;
import gestor.util.Etiquetado;
import gestor.util.Json;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cuenta por cobrar o por pagar. Saldo = valor inicial - abonos (RN-04). */
public class CuentaFinanciera {
    private final int id;
    private final TipoCuenta tipo;
    private final String tercero;
    private final BigDecimal monto;
    private final LocalDate vencimiento;
    private final List<Abono> abonos = new ArrayList<>();

    public CuentaFinanciera(int id, TipoCuenta tipo, String tercero, BigDecimal monto, LocalDate vencimiento) {
        if (tipo == null) throw new DatoInvalido("El tipo de cuenta debe ser cobrar o pagar.");
        this.id = id;
        this.tipo = tipo;
        this.tercero = Montos.exigirTexto(tercero, "tercero");
        this.monto = Montos.aMonto(monto);
        this.vencimiento = Montos.exigirFecha(vencimiento, "fecha de vencimiento");
    }

    public int id() { return id; }
    public TipoCuenta tipo() { return tipo; }
    public String tercero() { return tercero; }
    public BigDecimal monto() { return monto; }
    public LocalDate vencimiento() { return vencimiento; }
    public List<Abono> abonos() { return Collections.unmodifiableList(abonos); }

    public BigDecimal saldo() {
        return abonos.stream().map(Abono::monto).reduce(monto, BigDecimal::subtract);
    }

    /** RN-05 */
    public boolean saldada() { return saldo().signum() == 0; }

    /** RN-06 */
    public boolean vencida(LocalDate hoy) { return vencimiento.isBefore(hoy) && !saldada(); }

    public Abono abonar(BigDecimal valor, LocalDate fecha) {
        if (saldada()) throw new CuentaSaldada("La cuenta ya está saldada.");
        BigDecimal m;
        try {
            m = Montos.aMonto(valor);
        } catch (MontoInvalido e) {
            throw new AbonoInvalido(e.getMessage());
        }
        if (m.compareTo(saldo()) > 0) throw new AbonoInvalido("El abono supera el saldo pendiente.");
        Abono abono = new Abono(m, Montos.exigirFecha(fecha, "fecha"));
        abonos.add(abono);
        return abono;
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tipo", tipo.etiqueta());
        m.put("tercero", tercero);
        m.put("monto", monto.toPlainString());
        m.put("vencimiento", vencimiento.toString());
        m.put("abonos", abonos.stream().map(Abono::aMapa).toList());
        return m;
    }

    public static CuentaFinanciera desdeMapa(Map<String, Object> m) {
        CuentaFinanciera c = new CuentaFinanciera(Json.entero(m, "id"),
                Etiquetado.desde(TipoCuenta.class, Json.str(m, "tipo")), Json.str(m, "tercero"),
                new BigDecimal(Json.str(m, "monto")), LocalDate.parse(Json.str(m, "vencimiento")));
        Json.lista(m, "abonos").forEach(a -> c.abonos.add(Abono.desdeMapa(a)));
        return c;
    }
}
