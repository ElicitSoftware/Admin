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
