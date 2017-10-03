# oksql

oksql is a library for using postgres sql.

## Usage

Add `[oksql "1.0.0"]` to your `:dependencies` in your `project.clj`

Create a `.sql` file in your `resources/sql` folder like this one for example:

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

(def db {:connection-uri "postgres://localhost:5432/your_project_db"})

(defn all []
  (oksql/query :items/all))

(defn fetch [id]
  (oksql/query :items/fetch {:id id}))

(defn create [m]
  (oksql/query :items/insert m))

(defn update [id m]
  (oksql/query :items/update (merge {:id id} m)))

(defn delete [id]
  (oksql/delete :items/delete {:id id}))
```

A real advantage to this code (which can be generated statically) over the alternatives is that
you can add your own validation logic before and after these functions and you get go to definition
since there aren't any macros generating functions for you.

A good example of this:

```clojure
(ns your-project.models.users
  (:require [oksql.core :as oksql]))

(defn email? [str]
  (and
    (string? str)
    (some? (re-find #".+@.+\." str))))

(defn validate-email [{:keys [email] :as user}]
  (if (email? email)
    user
    (throw (Exception. "That's not an email"))))

(defn validate-password [{:keys [password] :as user}]
  (if (decent-password? password)
    user
    (throw (Exception. "Passwords need to be at least 12 characters"))))

(defn validate-matching-passwords [{:keys [password confirm-password] :as user}]
  (if (= password confirm-password)
    user
    (throw (Exception. "Passwords need to match"))))

(defn validate [{:keys [email password confirm-password] :as user}]
  (-> user
    validate-email
    validate-password
    validate-matching-passwords))

(defn insert [m]
  (let [user (validate m)]
    (oksql/query :items/insert m)))
```

## Why

The default for interacting with postgres from clojure without a library looks like this

```clojure
(def db {:connection-uri "postgres://localhost:5432/items_db})
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

So, fed up with these things and probably more, [yesql](https://github.com/krisajenkins/yesql) was born. Yesql basically solved my problems as well and I've been using it for the past two years on all of my projects. There are a few problems with yesql outlined in a fork here, [jeesql](https://github.com/tatut/jeesql).

Now jeesql solved my problems with yesql:

- Instaparse dependency
- No way to specify that you just want a single row

It also added some stuff that I didn't really need:
- Positional arguments
- Various query attributes

I also really didn't like the idea that I couldn't "go to definition" with `defqueries` since it generated the functions with a macro. I'd rather just take a page from rails' book and generate the code statically and have it sitting in files, at least then it's easy to see and change it later. So those are the main differences from yesql, hugsql, and jeesql:

- No defqueries macro
- Simple row-fn (`-- f: first`) support and that's it
- No symbolic representation of `returning *`, just declare it explicitly

## TODO

- Configurable location for sql files?
- Cache sql files
- Automatic reloading of sql files

## Inspiration

- [yesql](https://github.com/krisajenkins/yesql)
- [hugsql](https://github.com/layerware/hugsql)
- [jeesql](https://github.com/tatut/jeesql)