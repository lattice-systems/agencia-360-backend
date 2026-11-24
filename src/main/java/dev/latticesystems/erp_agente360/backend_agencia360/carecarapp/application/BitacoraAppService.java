package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.GastoVehiculo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.LecturaKm;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.TipoGasto;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.GastoVehiculoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.LecturaKmRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.NoProcesableException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora del conductor: gastos del vehículo (V2-M18.01 y V2-M18.04). Una
 * carga de gasolina también deja una lectura de km del cliente. Un vehículo o
 * gasto ajeno responde 404, como pide el contrato.
 */
@Service
public class BitacoraAppService {

	private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Direction.DESC, "fecha")
			.and(Sort.by(Sort.Direction.DESC, "id"));

	private final VehiculosDeClientes vehiculos;
	private final GastoVehiculoRepository gastos;
	private final LecturaKmRepository lecturas;
	private final KilometrajeService kilometraje;
	private final Clock reloj;
	private final ZoneId zona;

	@Autowired
	public BitacoraAppService(VehiculosDeClientes vehiculos, GastoVehiculoRepository gastos,
			LecturaKmRepository lecturas, KilometrajeService kilometraje) {
		this(vehiculos, gastos, lecturas, kilometraje, Clock.systemUTC(), ZoneId.of("America/Mexico_City"));
	}

	BitacoraAppService(VehiculosDeClientes vehiculos, GastoVehiculoRepository gastos, LecturaKmRepository lecturas,
			KilometrajeService kilometraje, Clock reloj, ZoneId zona) {
		this.vehiculos = vehiculos;
		this.gastos = gastos;
		this.lecturas = lecturas;
		this.kilometraje = kilometraje;
		this.reloj = reloj;
		this.zona = zona;
	}

	/**
	 * @param mes
	 *            {@code YYYY-MM}; {@code null} para todos
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si el mes o la página no son válidos (400)
	 */
	@Transactional(readOnly = true)
	public Pagina<GastoApp> listar(Long clienteId, Long vehiculoId, String mes, Integer pagina, Integer tamano) {
		Pageable base = Pagina.pedir(pagina, tamano);
		Pageable pedida = PageRequest.of(base.getPageNumber(), base.getPageSize(), RECIENTES_PRIMERO);
		YearMonth delMes = mes == null ? null : parsear(mes);
		propio(clienteId, vehiculoId);
		var resultado = delMes == null
				? gastos.findByVehiculoClienteId(vehiculoId, pedida)
				: gastos.findByVehiculoClienteIdAndFechaBetween(vehiculoId, delMes.atDay(1), delMes.atEndOfMonth(),
						pedida);
		return Pagina.de(resultado, GastoApp::de);
	}

	/**
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente (404)
	 * @throws IllegalArgumentException
	 *             si falta un dato o no cumple las reglas del tipo (400)
	 * @throws NoProcesableException
	 *             con {@code KM_INVALIDO} si es gasolina y el km es menor al
	 *             oficial o a la última lectura del cliente (422)
	 */
	@Transactional
	public GastoApp registrar(Long clienteId, Long vehiculoId, TipoGasto tipo, BigDecimal monto, BigDecimal litros,
			Integer km, LocalDate fecha, String descripcion) {
		VehiculoDeCliente vehiculo = propio(clienteId, vehiculoId);
		GastoVehiculo gasto = GastoVehiculo.registrar(vehiculoId, tipo, monto, litros, km, fecha, descripcion, hoy(),
				reloj.instant());
		if (tipo == TipoGasto.GASOLINA) {
			gasto.ligarLectura(kilometraje.registrar(vehiculo, km).getId());
		}
		return GastoApp.de(gastos.saveAndFlush(gasto));
	}

	/**
	 * Reemplaza el gasto. Si es gasolina, la lectura de km que lo acompaña se
	 * corrige (o se crea) y el km se compara sin contarla.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el gasto no existe o es de un vehículo ajeno (404)
	 * @throws IllegalArgumentException
	 *             si falta un dato o no cumple las reglas del tipo (400)
	 * @throws NoProcesableException
	 *             con {@code KM_INVALIDO} (422)
	 */
	@Transactional
	public GastoApp corregir(Long clienteId, Long gastoId, TipoGasto tipo, BigDecimal monto, BigDecimal litros,
			Integer km, LocalDate fecha, String descripcion) {
		GastoVehiculo gasto = propioGasto(clienteId, gastoId);
		VehiculoDeCliente vehiculo = propio(clienteId, gasto.getVehiculoClienteId());
		gasto.reemplazar(tipo, monto, litros, km, fecha, descripcion, hoy(), reloj.instant());
		if (tipo == TipoGasto.GASOLINA) {
			kilometraje.exigirNoMenorAlConocido(vehiculo, km, gasto.getLecturaKmId());
			if (gasto.getLecturaKmId() == null) {
				gasto.ligarLectura(kilometraje.registrar(vehiculo, km).getId());
			} else {
				lecturas.findById(gasto.getLecturaKmId()).ifPresent(lectura -> lectura.corregir(km));
			}
		}
		return GastoApp.de(gastos.saveAndFlush(gasto));
	}

	/**
	 * Borra el gasto; la lectura de km que creó se conserva.
	 *
	 * @throws RecursoNoEncontradoException
	 *             si el gasto no existe o es de un vehículo ajeno (404)
	 */
	@Transactional
	public void borrar(Long clienteId, Long gastoId) {
		gastos.delete(propioGasto(clienteId, gastoId));
	}

	private GastoVehiculo propioGasto(Long clienteId, Long gastoId) {
		GastoVehiculo gasto = gastos.findById(gastoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el gasto " + gastoId));
		propio(clienteId, gasto.getVehiculoClienteId());
		return gasto;
	}

	private VehiculoDeCliente propio(Long clienteId, Long vehiculoId) {
		return vehiculos.buscarPorId(vehiculoId).filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + vehiculoId));
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj.withZone(zona));
	}

	private static YearMonth parsear(String mes) {
		try {
			return YearMonth.parse(mes);
		} catch (DateTimeException invalido) {
			throw new IllegalArgumentException("El mes debe tener el formato AAAA-MM");
		}
	}
}
