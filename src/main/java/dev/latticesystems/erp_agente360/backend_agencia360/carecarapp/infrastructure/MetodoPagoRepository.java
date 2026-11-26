package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.MetodoPago;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {

	List<MetodoPago> findByClienteIdOrderByPredeterminadoDescIdAsc(Long clienteId);

	Optional<MetodoPago> findByIdAndClienteId(Long id, Long clienteId);

	Optional<MetodoPago> findByClienteIdAndTokenProcesador(Long clienteId, String tokenProcesador);

	long countByClienteId(Long clienteId);
}
