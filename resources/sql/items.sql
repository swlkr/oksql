-- name: all
-- Gets all of the items
select *
from items
order by created_at desc

-- name: fetch
-- f: first
-- Gets a single item
select *
from items
where id = :id

-- name: insert
-- f: first
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
-- f: first
update items
set name = :name
where id = :id
returning *

-- name: delete
-- f: first
delete
from items
where id = :id
returning *
