create table archivo (
    clave          varchar(80)  primary key,
    tipo_contenido varchar(100) not null,
    tamano_bytes   bigint       not null,
    contenido      bytea        not null,
    created_at     timestamp(6) with time zone not null default now(),
    constraint ck_archivo_tamano check (tamano_bytes > 0 and tamano_bytes = octet_length(contenido))
);
