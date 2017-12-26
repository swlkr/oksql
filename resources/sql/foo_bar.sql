-- foo_bar.sql

-- name: all
-- Gets all of the items
select *
from foo_bar

-- name: fetch
-- fn: first
-- Gets a single item
select *
from foo_bar
where name = :name

-- name: where
where name = :name
returning *
