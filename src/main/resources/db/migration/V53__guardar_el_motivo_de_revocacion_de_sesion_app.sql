-- Por qué se revocó cada sesión de la app.
--
-- No es auditoría: decide si reusar un refresh token se toma como posible robo.
-- Hasta ahora `revocada_en` no distinguía «la consumió un refresco» de «una
-- persona cerró esta sesión», y las dos disparaban el cierre de TODAS las
-- sesiones de la cuenta.
--
-- Eso vuelve inservible «cerrar mis sesiones abiertas» (V2-M01.09): el
-- dispositivo que acaban de cerrar todavía tiene su token y lo usa en su
-- siguiente intento, así que cerrar la sesión de una tablet dejaba al cliente
-- fuera de su propio celular. Con un celular robado, el ladrón conseguía una
-- negación de servicio sobre la cuenta.
--
-- Solo REFRESCO es señal de robo: es el único caso en el que el token siguió
-- existiendo después de usarse, así que si llega otra vez alguien tiene copia.
alter table sesion_app
    add column motivo_revocacion varchar(20);

-- Las filas revocadas antes de esta migración quedan en nulo. `refrescar` trata
-- el nulo como REFRESCO, que es el comportamiento de hoy: casi todas lo son, y
-- ante la duda conviene equivocarse del lado de cerrar sesiones y no del de
-- dejar viva una copia robada.
alter table sesion_app
    add constraint ck_sesion_app_motivo
        check (motivo_revocacion is null or motivo_revocacion in ('REFRESCO', 'CIERRE'));

-- Revocada y sin motivo solo puede pasar con las filas viejas: de aquí en
-- adelante, quien revoca dice por qué.
comment on column sesion_app.motivo_revocacion is
    'REFRESCO (la consumió un refresco; reusarla es señal de robo) o CIERRE (la cerró una persona). Nulo en las revocadas antes de V2-M01.09.';
