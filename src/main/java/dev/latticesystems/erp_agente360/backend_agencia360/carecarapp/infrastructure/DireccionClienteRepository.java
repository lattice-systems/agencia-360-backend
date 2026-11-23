package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.DireccionCliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DireccionClienteRepository extends JpaRepository<DireccionCliente, Long> {

	List<DireccionCliente> findByClienteIdOrderByIdAsc(Long clienteId);

	Optional<DireccionCliente> findByIdAndClienteId(Long id, Long clienteId);
}
