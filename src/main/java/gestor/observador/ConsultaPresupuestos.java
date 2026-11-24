package gestor.observador;

import gestor.dominio.Presupuesto;
import java.math.BigDecimal;
import java.util.List;

/** Lo único que el observador de presupuestos necesita del gestor (evita depender de su clase). */
public interface ConsultaPresupuestos {
    List<Presupuesto> presupuestosDe(String categoria);

    BigDecimal consumo(Presupuesto presupuesto);
}
