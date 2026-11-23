-- APP-3.1 · Los PDF de los manuales se guardan en la base.
--
-- En Azure Container Apps el disco del contenedor no se conserva: cada
-- revisión nueva o reinicio borraba los PDF y la descarga daba 404. Guardarlos
-- aquí no cuesta nada (cabe en el plan gratis de Neon) y no cambia el
-- contrato: manual.archivo sigue siendo la clave del archivo.

create table archivo_manual (
    clave      varchar(200) primary key,
    contenido  bytea        not null,
    created_at timestamp(6) with time zone not null default now()
);
