-- V2-M02.08 · Motivo de la cita que el cliente elige al agendar desde la app.
--
-- Es opcional: las citas anteriores y las que no lo traen quedan con motivo
-- nulo. Se guarda el nombre del enum en mayúsculas; la API lo expone en
-- minúsculas.
alter table cita add column motivo varchar(15);

alter table cita add constraint ck_cita_motivo
    check (motivo is null or motivo in ('MANTENIMIENTO', 'REPARACION', 'REVISION', 'GARANTIA', 'OTRO'));
