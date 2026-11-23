package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.CalculadoraTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstimacionTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ModeloCatalogo;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ParametroTomaCuenta;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.EstimacionTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ModeloCatalogoRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure.ParametroTomaCuentaRepository;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes;
import dev.latticesystems.erp_agente360.backend_agencia360.clientes.VehiculosDeClientes.VehiculoDeCliente;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.RecursoNoEncontradoException;
import dev.latticesystems.erp_agente360.backend_agencia360.shared.exception.SinParametrosDeValuacionException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toma a cuenta en la app (V2-M03.04, V2-M03.06 y V2-M03.07): calcula cuánto
 * darían por el auto del cliente con los parámetros que mantiene la agencia, lo
 * guarda en su historial y lo lista. Calcular y guardar es una sola operación.
 * El cliente sale del token: una estimación ajena no se puede leer y un
 * vehículo ajeno responde 404.
 */
@Service
public class TomaCuentaService {

	static final int KM_MAXIMO = 999_999;
	static final int ANIO_MINIMO = 1900;

	private final ParametroTomaCuentaRepository parametroRepository;
	private final EstimacionTomaCuentaRepository estimacionRepository;
	private final ModeloCatalogoRepository catalogoRepository;
	private final VehiculosDeClientes vehiculos;
	private final CalculadoraTomaCuenta calculadora;
	private final ObjectMapper json;
	private final Clock reloj;

	@Autowired
	public TomaCuentaService(ParametroTomaCuentaRepository parametroRepository,
			EstimacionTomaCuentaRepository estimacionRepository, ModeloCatalogoRepository catalogoRepository,
			VehiculosDeClientes vehiculos, ObjectMapper json,
			@Value("${carecarapp.toma-cuenta.km-anuales:15000}") int kmAnuales,
			@Value("${carecarapp.toma-cuenta.margen:0.08}") BigDecimal margen) {
		this(parametroRepository, estimacionRepository, catalogoRepository, vehiculos, json,
				new CalculadoraTomaCuenta(kmAnuales, margen), Clock.systemUTC());
	}

	TomaCuentaService(ParametroTomaCuentaRepository parametroRepository,
			EstimacionTomaCuentaRepository estimacionRepository, ModeloCatalogoRepository catalogoRepository,
			VehiculosDeClientes vehiculos, ObjectMapper json, CalculadoraTomaCuenta calculadora, Clock reloj) {
		this.parametroRepository = parametroRepository;
		this.estimacionRepository = estimacionRepository;
		this.catalogoRepository = catalogoRepository;
		this.vehiculos = vehiculos;
		this.json = json;
		this.calculadora = calculadora;
		this.reloj = reloj;
	}

	/**
	 * Calcula el rango y lo guarda en el historial del cliente.
	 *
	 * @throws IllegalArgumentException
	 *             si faltan datos, el kilometraje o el año salen de rango, o el
	 *             auto viene de las dos formas o de ninguna (400)
	 * @throws RecursoNoEncontradoException
	 *             si el vehículo no existe o no es del cliente, o el modelo del
	 *             catálogo no existe o está retirado (404)
	 * @throws SinParametrosDeValuacionException
	 *             si la agencia no tiene parámetros activos para ese auto (422)
	 */
	@Transactional
	public EstimacionTomaCuentaApp estimar(Long clienteId, SolicitudEstimacion solicitud) {
		int anioActual = Year.now(reloj).getValue();
		validarDatos(solicitud, anioActual);

		VehiculoDeCliente vehiculo = solicitud.vehiculoId() == null ? null : vehiculoDelCliente(clienteId, solicitud);
		String marca = vehiculo != null ? vehiculo.marca() : solicitud.marca().strip();
		String modelo = vehiculo != null ? vehiculo.modelo() : solicitud.modelo().strip();
		int anio = vehiculo != null ? vehiculo.anio() : solicitud.anio();

		ModeloCatalogo destino = solicitud.modeloCatalogoId() == null ? null : modeloVigente(solicitud);
		ParametroTomaCuenta parametro = parametroPara(marca, modelo, anio);

		var resultado = calculadora.calcular(parametro, anioActual, anio, solicitud.km(), solicitud.estado());
		var supuestos = new SupuestosEstimacionApp(resultado.valorBase(), resultado.edadAnios(), resultado.kmEsperado(),
				resultado.ajusteKm(), resultado.ajusteEstado(), resultado.margen());
		Long vehiculoId = vehiculo == null ? null : vehiculo.id();
		String nombreDestino = destino == null ? null : nombreDe(destino);
		BigDecimal precioDestino = destino == null ? null : destino.getPrecioDesde();
		String marcaGuardada = marca != null ? marca : parametro.getMarca();
		EstimacionTomaCuenta nueva = EstimacionTomaCuenta.nueva(clienteId, vehiculoId, solicitud.modeloCatalogoId(),
				marcaGuardada, modelo, anio, solicitud.km(), solicitud.estado(), parametro.getId(), resultado,
				comoJson(supuestos), nombreDestino, precioDestino);
		EstimacionTomaCuenta guardada = estimacionRepository.save(nueva);
		return convertir(guardada);
	}

	/**
	 * Las estimaciones del cliente, de la más reciente a la más antigua.
	 *
	 * @throws IllegalArgumentException
	 *             si la página es negativa o el tamaño no está entre 1 y 50 (400)
	 */
	@Transactional(readOnly = true)
	public Pagina<EstimacionTomaCuentaApp> listar(Long clienteId, Integer pagina, Integer tamano) {
		Pageable pedida = Pagina.pedir(pagina, tamano);
		return Pagina.de(estimacionRepository.findByClienteIdOrderByCreatedAtDescIdDesc(clienteId, pedida),
				this::convertir);
	}

	private void validarDatos(SolicitudEstimacion solicitud, int anioActual) {
		if (solicitud.km() == null || solicitud.km() < 0 || solicitud.km() > KM_MAXIMO) {
			throw new IllegalArgumentException("El kilometraje debe estar entre 0 y " + KM_MAXIMO);
		}
		if (solicitud.estado() == null) {
			throw new IllegalArgumentException("El estado del auto es obligatorio");
		}
		boolean conVehiculo = solicitud.vehiculoId() != null;
		boolean capturado = !vacio(solicitud.marca()) || !vacio(solicitud.modelo()) || solicitud.anio() != null;
		if (conVehiculo == capturado) {
			throw new IllegalArgumentException(
					"Indica el auto con vehiculoId o con marca, modelo y anio, pero no con las dos formas");
		}
		if (conVehiculo) {
			return;
		}
		if (vacio(solicitud.marca()) || vacio(solicitud.modelo()) || solicitud.anio() == null) {
			throw new IllegalArgumentException("Para capturar el auto faltan marca, modelo o anio");
		}
		if (solicitud.marca().strip().length() > 40 || solicitud.modelo().strip().length() > 80) {
			throw new IllegalArgumentException("La marca pasa de 40 caracteres o el modelo de 80");
		}
		if (solicitud.anio() < ANIO_MINIMO || solicitud.anio() > anioActual) {
			throw new IllegalArgumentException("El año debe estar entre " + ANIO_MINIMO + " y " + anioActual);
		}
	}

	private VehiculoDeCliente vehiculoDelCliente(Long clienteId, SolicitudEstimacion solicitud) {
		return vehiculos.buscarPorId(solicitud.vehiculoId())
				.filter(encontrado -> encontrado.propietario().id().equals(clienteId))
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + solicitud.vehiculoId()));
	}

	private ModeloCatalogo modeloVigente(SolicitudEstimacion solicitud) {
		return catalogoRepository.findById(solicitud.modeloCatalogoId()).filter(ModeloCatalogo::isActivo).orElseThrow(
				() -> new RecursoNoEncontradoException("No existe el modelo " + solicitud.modeloCatalogoId()));
	}

	/**
	 * El parámetro activo de esa marca, modelo y año. Un vehículo registrado sin
	 * marca se busca solo por modelo y año, y solo sirve si hay uno.
	 */
	private ParametroTomaCuenta parametroPara(String marca, String modelo, int anio) {
		Optional<ParametroTomaCuenta> parametro;
		if (marca == null) {
			List<ParametroTomaCuenta> candidatos = parametroRepository
					.findByActivoTrueAndModeloIgnoreCaseAndAnio(modelo, anio);
			parametro = candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
		} else {
			parametro = parametroRepository.findByActivoTrueAndMarcaIgnoreCaseAndModeloIgnoreCaseAndAnio(marca, modelo,
					anio);
		}
		return parametro.orElseThrow(() -> new SinParametrosDeValuacionException(
				"Todavía no tenemos datos para valuar ese auto. Puedes pedir una valuación formal."));
	}

	private static String nombreDe(ModeloCatalogo modelo) {
		return modelo.getMarca() + " " + modelo.getModelo() + " " + modelo.getAnio();
	}

	private String comoJson(SupuestosEstimacionApp supuestos) {
		try {
			return json.writeValueAsString(supuestos);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("No se pudieron guardar los supuestos de la estimación", e);
		}
	}

	private EstimacionTomaCuentaApp convertir(EstimacionTomaCuenta estimacion) {
		try {
			return new EstimacionTomaCuentaApp(estimacion.getId(), estimacion.getCreatedAt(),
					estimacion.getVehiculoClienteId(), estimacion.getMarca(), estimacion.getModelo(),
					estimacion.getAnio(), estimacion.getKm(), estimacion.getEstado(), estimacion.getModeloCatalogoId(),
					estimacion.getModeloDestino(), estimacion.getPrecioDestino(), estimacion.getMinimo(),
					estimacion.getMaximo(), json.readValue(estimacion.getSupuestos(), SupuestosEstimacionApp.class));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Los supuestos de la estimación " + estimacion.getId() + " no se leen", e);
		}
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}
}
