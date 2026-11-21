package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Página de una lista que crece con el tiempo, como pide el contrato de la app:
 * {@code pagina} desde 0 y {@code tamano} de 1 a 50 (20 por omisión).
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

	public static final int TAMANO_POR_OMISION = 20;
	public static final int TAMANO_MAXIMO = 50;

	/**
	 * @throws IllegalArgumentException
	 *             si la página es negativa o el tamaño no está entre 1 y 50 (400)
	 */
	public static Pageable pedir(Integer pagina, Integer tamano) {
		int numero = pagina == null ? 0 : pagina;
		int elementos = tamano == null ? TAMANO_POR_OMISION : tamano;
		if (numero < 0) {
			throw new IllegalArgumentException("La página empieza en 0");
		}
		if (elementos < 1 || elementos > TAMANO_MAXIMO) {
			throw new IllegalArgumentException("El tamaño de la página debe estar entre 1 y " + TAMANO_MAXIMO);
		}
		return PageRequest.of(numero, elementos);
	}

	public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> convertir) {
		return new Pagina<>(pagina.getContent().stream().map(convertir).toList(), pagina.getNumber(), pagina.getSize(),
				pagina.getTotalElements(), pagina.getTotalPages());
	}
}
