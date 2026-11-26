package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.EstadoReferido;
import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.Referido;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferidoRepository extends JpaRepository<Referido, Long> {

	List<Referido> findByClienteIdOrderByCreadoEnDescIdDesc(Long clienteId);

	long countByClienteIdAndEstadoIn(Long clienteId, Collection<EstadoReferido> estados);

	boolean existsByTelefono(String telefono);

	boolean existsByCorreoIgnoreCase(String correo);

	Optional<Referido> findByProspectoId(Long prospectoId);
}
