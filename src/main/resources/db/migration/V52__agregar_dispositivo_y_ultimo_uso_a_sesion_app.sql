-- V2-M01.09: ver y cerrar las sesiones abiertas de la cuenta.
--
-- La ficha nombra `sesion_app` con `dispositivo` y `ultimo_uso`; la tabla nació
-- sin ellos (V10). No se renombra ni se quita nada.
--
-- Las tres columnas son nulas porque las sesiones que ya existen no tienen el
-- dato y **siguen siendo válidas**: la app muestra un texto genérico en su
-- lugar. Exigirlas dejaría fuera justo a quien ya estaba dentro.
alter table sesion_app
    add column dispositivo varchar(80);

alter table sesion_app
    add column ultimo_uso timestamp(6) with time zone;

-- Por qué hace falta `iniciada_en` y no basta `created_at`: el refresco revoca
-- la fila y crea otra, así que `created_at` es el momento del último refresco.
-- Una sesión abierta hace 20 días se vería «iniciada hace una hora» y el dato
-- sería inservible. `iniciada_en` se copia de una fila a la siguiente en cada
-- refresco, así que sí dice cuándo empezó la sesión de verdad.
alter table sesion_app
    add column iniciada_en timestamp(6) with time zone;

-- La lista se pide por cuenta y se ordena por último uso; el índice de V10 es
-- solo por cuenta.
create index idx_sesion_app_cuenta_vigente on sesion_app (cuenta_id, ultimo_uso desc);
