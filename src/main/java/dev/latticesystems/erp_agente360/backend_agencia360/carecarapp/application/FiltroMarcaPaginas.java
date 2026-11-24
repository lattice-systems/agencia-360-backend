package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separa la respuesta del asistente que llega en trozos (V2-M24.09) de la marca
 * final {@code PAGINAS: 2, 7} (o {@code PAGINAS: ninguna}) que pide el prompt.
 * Solo deja pasar líneas completas, así la marca nunca llega a la app.
 */
public class FiltroMarcaPaginas {

	private static final Pattern MARCA = Pattern.compile("(?i)^\\s*PAGINAS\\s*:\\s*(.*)$");
	private static final Pattern NUMERO = Pattern.compile("\\d+");

	private final Consumer<String> alMostrar;
	private final StringBuilder mostrado = new StringBuilder();
	private final StringBuilder pendiente = new StringBuilder();

	/** Lo que termina un fragmento: texto visible, páginas y si lo encontró. */
	public record Resultado(String texto, List<Integer> paginas, boolean encontrado) {
	}

	public FiltroMarcaPaginas(Consumer<String> alMostrar) {
		this.alMostrar = alMostrar;
	}

	/** Agrega un trozo; muestra las líneas que ya están completas. */
	public void agregar(String trozo) {
		pendiente.append(trozo);
		int corte = pendiente.lastIndexOf("\n");
		if (corte < 0) {
			return;
		}
		String completas = pendiente.substring(0, corte);
		pendiente.delete(0, corte + 1);
		mostrar(completas);
	}

	/** Lo que ya se mostró, para guardar una respuesta parcial. */
	public String mostrado() {
		return mostrado.toString();
	}

	/** Cierra: la última línea es la marca o es texto. */
	public Resultado terminar() {
		String ultima = pendiente.toString();
		pendiente.setLength(0);
		Matcher marca = MARCA.matcher(ultima);
		if (!marca.matches()) {
			mostrar(ultima);
			return new Resultado(mostrado.toString().strip(), List.of(), !mostrado.isEmpty());
		}
		List<Integer> paginas = new ArrayList<>();
		Matcher numero = NUMERO.matcher(marca.group(1));
		while (numero.find()) {
			paginas.add(Integer.parseInt(numero.group()));
		}
		return new Resultado(mostrado.toString().strip(), paginas, !paginas.isEmpty());
	}

	private void mostrar(String texto) {
		if (texto.isEmpty()) {
			return;
		}
		String trozo = mostrado.isEmpty() ? texto : "\n" + texto;
		mostrado.append(trozo);
		alMostrar.accept(trozo);
	}
}
