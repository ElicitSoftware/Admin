# Flyway migration naming

New migrations should use `PascalCase_With_Underscores` for the description
part of the filename (e.g. `V0.0.12__Add_Something_New.sql`), matching every
migration from `V0.0.4` onward.

`V0.0.1__CREATE_ADMIN_SCHEMA.sql`, `V0.0.2__ADMIN_GRANTS.sql`, and
`V0.0.3__POPULATE_DEV_DATA.sql` use an older `ALL_CAPS_SNAKE` convention.
**Do not rename them** (or any other already-applied migration) to match -
Flyway validates applied migrations against a checksum of their filename and
contents, so renaming one that has already run in any environment breaks
`validate-on-migrate` there.

# Migrations edited after they were applied

A few already-applied migrations have been edited in place. Each edit is only safe
because `quarkus.flyway.owner.repair-at-start=true` rewrites the recorded checksum
on existing databases instead of failing validation:

- `V0.0.3` - its `user_surveys` insert is conditional, so a fresh database with no
  survey can start.
- `V0.0.1` and `V0.0.7` - the `survey.status` view selects `r.access_code`
  instead of `r.token`. Survey's V014 renames that column before Admin migrates, so
  a fresh database could not create the view otherwise. Existing databases get the
  view's output column renamed by `V0.0.17` instead.

Prefer a new migration whenever one will do. Edit an applied migration only when a
fresh database cannot get past it.
