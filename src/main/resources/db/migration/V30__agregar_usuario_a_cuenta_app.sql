alter table cuenta_app add column usuario varchar(120);

update cuenta_app ca
set usuario = c.correo
from cliente c
where c.id = ca.cliente_id;

update cuenta_app
set usuario = 'cliente-' || cliente_id
where usuario is null;

alter table cuenta_app alter column usuario set not null;
alter table cuenta_app add constraint uq_cuenta_app_usuario unique (usuario);
