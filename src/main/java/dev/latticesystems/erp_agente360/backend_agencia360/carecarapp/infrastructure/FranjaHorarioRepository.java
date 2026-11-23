package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.infrastructure;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.domain.FranjaHorario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FranjaHorarioRepository extends JpaRepository<FranjaHorario, Long> {

	List<FranjaHorario> findByActivaTrueOrderByDiaSemanaAscHoraInicioAsc();

	Optional<FranjaHorario> findByIdAndActivaTrue(Long id);
}
