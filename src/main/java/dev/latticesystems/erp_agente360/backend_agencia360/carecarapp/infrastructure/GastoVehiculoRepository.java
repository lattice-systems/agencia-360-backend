package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.GastoVehiculo;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoVehiculoRepository extends JpaRepository<GastoVehiculo, Long> {

	Page<GastoVehiculo> findByVehiculoClienteId(Long vehiculoClienteId, Pageable pagina);

	Page<GastoVehiculo> findByVehiculoClienteIdAndFechaBetween(Long vehiculoClienteId, LocalDate desde, LocalDate hasta,
			Pageable pagina);
}
