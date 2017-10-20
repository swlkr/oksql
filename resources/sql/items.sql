-- items.sql

-- name: all
-- Gets all of the items
select *
from items
order by created_at desc

-- name: fetch
-- fn: first
-- Gets a single item
select *
from items
where id = :id

-- name: insert
-- fn: first
insert into items (
  id,
  name,
  created_at
)
values (
  :id,
  :name,
  :created_at
)
returning *

-- name: update
-- fn: first
update items
set name = :name, alias = :alias
where id = :id
returning *

-- name: delete
-- fn: first
delete
from items

-- name: where
where id = :id
returning *
