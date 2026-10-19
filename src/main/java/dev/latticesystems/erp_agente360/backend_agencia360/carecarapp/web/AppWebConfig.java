package dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.web;

import dev.latticesystems.erp_agente360.backend_agencia360.carecarapp.application.AccesoAppService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Revisión de cuenta activa en las rutas protegidas de la app. */
@Configuration
class AppWebConfig implements WebMvcConfigurer {

	private final AccesoAppService accesoAppService;

	AppWebConfig(AccesoAppService accesoAppService) {
		this.accesoAppService = accesoAppService;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new CuentaActivaInterceptor(accesoAppService)).addPathPatterns("/api/v1/app/**")
				.excludePathPatterns("/api/v1/app/auth/login", "/api/v1/app/auth/refrescar",
						"/api/v1/app/auth/cerrar-sesion", "/api/v1/app/auth/recuperar-contrasena",
						"/api/v1/app/auth/restablecer-contrasena");
	}
}
