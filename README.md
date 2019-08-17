# oksql

oksql is a library for using postgres.

## Usage

Add `[oksql "1.3.2"]` to your `:dependencies` in your `project.clj`

*or*

Add `oksql {:mvn/version "1.3.2"}` to your `deps.edn` file

Then, create a `.sql` file in your `resources/sql` folder like this one for example:

```sql
-- items.sql

-- name: fetch
-- fn: first
select *
from items
where id = :id

-- name: all
select *
from items
order by created_at desc

-- name: insert
-- fn: first
insert into items (id, name, created_at)
values (:id, :name, :created_at)
returning *

-- name: update
-- fn: first
update items
set name = :name
where id = :id
returning *

-- name: delete
-- fn: first
delete
from items
where id = :id
returning *
```

Then create a file to wire up your queries and you're done!

```clojure
(ns your-project.models.items
  (:require [oksql.core :as oksql])
  (:refer-clojure :exclude [update]))

(def db {:connection-uri "jdbc:postgresql://localhost:5432/your_project_db"})

(def query (partial oksql/query db))

(defn all []
  (query :items/all))

(defn fetch [id]
  (query :items/fetch {:id id}))

(defn create [{:keys [name created-at]}]
  (query :items/insert {:name name
                        :created-at created-at})) ; implicit! kebab -> snake case

(defn update [id m]
  (query :items/update (merge {:id id} m)))

(defn delete [id]
  (query :items/delete {:id id}))
```

A real advantage to this code (which can be generated statically) over the alternatives is that
you can add your own validation logic before and after these functions and you get go to definition
since there aren't any macros generating functions for you.

A good example of this:

```clojure
(ns your-project.models.users
  (:require [oksql.core :as oksql]))

(defn validate [user]
  (if (contains? user :email)
    user
    (throw (Exception. "Email required"))))

(defn insert [m]
  (let [user (validate m)]
    (oksql/query :items/insert m)))
```

So I know what you're thinking, man that sucks to keep the super simple write sql parts (insert/update/delete)
in sync every time I make a change to a table. Well.

```clojure
; namespace keyword corresponds to db schema name
; public by default, just like postgres
(oksql/update db :items {:name "update name"} :items/where {:id 123})

(oksql/insert db :items {:name "new item"})

(oksql/delete db :items :items/where {:id 123})
```

So to review, oksql is kind of an ORM now, but not really! Only for writes if you want them.

You still have the full power of sql at your disposal, but for writing, it's kind of unnecessary.
Here are the four functions again:

```clojure
(ns your-project.models.items
  (:require [oksql.core :as oksql])
  (:refer-clojure :exclude [update]))

(def db {:connection-uri "jdbc:postgresql://localhost:5432/your_project_db"})

(def query (partial oksql/query db))

(defn all []
  (query :items/all))

(defn fetch [id]
  (query :items/fetch {:id id}))

(defn create [m]
  (oksql/insert db :items m))

(defn update [id m]
  (oksql/update db :items m :items/where {:id id}))

(defn delete [id]
  (oksql/delete db :items :items/where {:id id}))
```

## Why

The default for interacting with postgres from clojure without a library looks like this

```clojure
(def db {:connection-uri "jdbc:postgresql://localhost:5432/items_db"})
(jdbc/query db ["select items.id, items.name, items.created_at from items where id = ?" 123])
```

It's not too bad with a short query, but you can imagine several joins and many to many relationships getting
out of hand. Plus there probably isn't any syntax highlighting in your editor for sql strings in a `.clj` file.

So you might graduate to something like this

```clojure
(select item
  (fields :id :name :created_at)
  (where {:id 123}))
```

It's a lot nicer syntactically, but you're now writing a sql dsl, not sql. So you have to know not only sql
but a clojure sql dsl which will eventually [not have the thing you're looking for](https://github.com/korma/Korma/issues/379).

So, fed up with these things and probably more, [yesql](https://github.com/krisajenkins/yesql) was born. Yesql basically solved my problems and I've been using it for the past two years on all of my projects. There are a few problems with yesql outlined in a fork here, [jeesql](https://github.com/tatut/jeesql).

jeesql solved most of my problems with yesql:

- No instaparse dependency
- No way to specify that you just want a single row

Unfortunately it added some stuff that I didn't really need:
- Positional arguments
- Various query attributes

I also didn't like that I couldn't "go to definition" with `defqueries` since it generated the functions with a macro. I'd rather just take a page from rails' book and generate the code statically and have it sitting in files, at least then it's easy to see and change it later. So those are the main differences from yesql, hugsql, and jeesql:

- No defqueries macro
- Simple results fn support (`-- fn: first`) support and that's it
- No symbolic representation of `returning *`, just declare it explicitly
- Implicit! Conversion of snake case to kebab case to and from the database!

## Inspiration

- [yesql](https://github.com/krisajenkins/yesql)
- [hugsql](https://github.com/layerware/hugsql)
- [jeesql](https://github.com/tatut/jeesql)
