-- Avviso al cliente quando un appuntamento viene segnato come no-show.
-- Un tipo a sé, non GENERIC: le app e i log possono riconoscerlo.
alter type notification_kind add value if not exists 'BOOKING_NO_SHOW';
