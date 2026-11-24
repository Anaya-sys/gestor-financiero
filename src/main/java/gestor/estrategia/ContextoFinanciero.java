package gestor.estrategia;

import gestor.dominio.CuentaFinanciera;
import gestor.dominio.Movimiento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos que reciben las estrategias. `movimientos` son solo los activos del periodo consultado. */
public record ContextoFinanciero(List<Movimiento> movimientos, List<CuentaFinanciera> cuentas,
                                 BigDecimal saldoInicial, LocalDate hoy) {}
