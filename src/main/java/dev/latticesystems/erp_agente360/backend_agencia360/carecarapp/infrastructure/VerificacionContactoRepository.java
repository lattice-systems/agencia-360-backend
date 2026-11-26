package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.VerificacionContacto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacionContactoRepository extends JpaRepository<VerificacionContacto, Long> {

	/**
	 * La verificación de ese cliente. Se filtra por cliente aquí y no después: una
	 * verificación ajena tiene que responder 404 y no 403, así que no debe llegar a
	 * manos del servicio.
	 */
	Optional<VerificacionContacto> findByIdAndClienteId(Long id, Long clienteId);
}
