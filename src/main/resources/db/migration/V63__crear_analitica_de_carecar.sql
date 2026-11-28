create table embudo_cita_analitica (
    intento uuid primary key,
    etapa smallint not null check (etapa between 1 and 3),
    cita_id bigint references cita(id) on delete set null,
    iniciado_en timestamp with time zone not null default now()
);

create index idx_embudo_cita_analitica_inicio on embudo_cita_analitica (iniciado_en);

create table diagnostico_app_diario (
    dia date primary key,
    errores_flutter bigint not null default 0,
    errores_asincronos bigint not null default 0,
    muestras bigint not null default 0,
    arranque_ms bigint not null default 0,
    primera_pantalla_ms bigint not null default 0,
    cuadros_medidos bigint not null default 0,
    cuadros_perdidos bigint not null default 0
);
