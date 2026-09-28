-- Tabla del registro de publicación de eventos de Spring Modulith (spring-modulith-starter-jpa).
-- No es modelo de datos de negocio: es infraestructura del propio framework para
-- garantizar la entrega de eventos de dominio entre módulos. El modelo de datos de
-- negocio sigue pendiente de definir (sección 9 del documento de arquitectura).
create table event_publication (
    id                uuid not null,
    listener_id       varchar(512) not null,
    event_type        varchar(512) not null,
    serialized_event  varchar(4000) not null,
    publication_date  timestamp(6) with time zone not null,
    completion_date   timestamp(6) with time zone,
    primary key (id)
);

create index idx_event_publication_completion_date on event_publication (completion_date);
