package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.ParametroTomaCuenta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroTomaCuentaRepository extends JpaRepository<ParametroTomaCuenta, Long> {

	Optional<ParametroTomaCuenta> findByActivoTrueAndMarcaIgnoreCaseAndModeloIgnoreCaseAndAnio(String marca,
			String modelo, int anio);

	/** Para los vehículos registrados sin marca: solo por modelo y año. */
	List<ParametroTomaCuenta> findByActivoTrueAndModeloIgnoreCaseAndAnio(String modelo, int anio);
}
