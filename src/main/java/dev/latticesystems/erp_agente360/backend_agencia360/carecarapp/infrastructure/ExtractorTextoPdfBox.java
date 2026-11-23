package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.ExtractorTextoPdf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extrae el texto por página con Apache PDFBox. */
@Component
public class ExtractorTextoPdfBox implements ExtractorTextoPdf {

	@Override
	public List<String> paginas(byte[] pdf) {
		try (PDDocument documento = Loader.loadPDF(pdf)) {
			PDFTextStripper extractor = new PDFTextStripper();
			List<String> paginas = new ArrayList<>();
			for (int pagina = 1; pagina <= documento.getNumberOfPages(); pagina++) {
				extractor.setStartPage(pagina);
				extractor.setEndPage(pagina);
				paginas.add(extractor.getText(documento).strip());
			}
			return paginas;
		} catch (IOException ex) {
			throw new IllegalArgumentException("El manual no es un PDF legible", ex);
		}
	}
}
