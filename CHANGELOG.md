# Release Notes
All notable changes to this package will be documented in this file.

- Added `normalizeQueryDate` for loose query/form inputs (sentinels, unix s/ms, Jalali vs Gregorian auto-detect by year band; default `1…1899`, overridable via `jalaliYearMin` / `jalaliYearMax`)
- `fromPersian` also converts Arabic-Indic digits (`٠-٩`)
- `parseJalali` accepts `.` as a date separator

## 0.1.0
- Initial commit
