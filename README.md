# JalaliX - Scala

Jalali (Persian / Shamsi) calendar for Scala 2.13+. Pure `java.time` (`ZoneId`), no runtime dependencies beyond the JDK.

[![license](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)

<!-- jalalix:package lang=scala maven=com.jalalix.scala:jalalix api=JalaliX parity=golden -->

```scala
import com.jalalix.scala.{JalaliX, JalaliFormatOptions}

val date = JalaliX.create("2025-02-25")

date.format("YYYY/MM/DD") // 1403/12/07
date.format("DD MMM YYYY", JalaliFormatOptions(persianDigits = true)) // ۰۷ اسفند ۱۴۰۳

JalaliX.parseJalali("1403/12/07").toISODate // 2025-02-25
```

`toISODate` uses wall-clock getters in the active timezone.

## Install

Maven coordinates: `com.jalalix.scala:jalalix:0.2.0`

```scala
libraryDependencies += "com.jalalix.scala" %% "jalalix" % "0.2.0"
```

```bash
./sbt test
./sbt publishLocal
./sbt publishSigned sonaUpload
```

## Usage

### create / format

```scala
val now = JalaliX.create(System.currentTimeMillis(), JalaliOptions(timezone = Some("Asia/Tehran")))

now.format("YYYY/MM/DD")
now.format("WN، DD MMM YYYY")
now.format("YYYY/MM/DD", persianDigits = true)
now.format("DD MMM YYYY", JalaliFormatOptions(calendar = Some("gregorian")))
```

`format` overloads: no-arg flags, `JalaliFormatOptions`, or `(persianDigits, gregorian)` booleans.

### parseJalali

```scala
JalaliX.parseJalali("1403/12/07")
JalaliX.parseJalali("۱۴۰۳-۱۲-۰۷ 14:30")
JalaliX.parseJalali("1403.12.07")
```

Named `parseJalali` to mirror the TypeScript API. Accepts `/`, `-`, or `.` as the date separator.

### normalizeQueryDate

Soft-normalize loose API/query date values to a Gregorian `YYYY-MM-DD HH:mm:ss` string (or `null`). Does not throw on junk input.

```scala
JalaliX.normalizeQueryDate("1403/12/07", Some(JalaliOptions(timezone = Some("Asia/Tehran"))))
// "2025-02-25 00:00:00"

JalaliX.normalizeQueryDate("undefined") // null
JalaliX.normalizeQueryDate(1740430800000L, Some(JalaliOptions(timezone = Some("Asia/Tehran"))))
// "2025-02-25 00:30:00"
```

Handles sentinels, unix timestamps (seconds or milliseconds; non-positive → `null`), Persian/Arabic-Indic digits, `/` `-` `.` separators, fractional seconds, optional `Z` / offset suffixes.

Detects Jalali when the year is in `jalaliYearMin`…`jalaliYearMax` (default `1`…`1899`) **and** the civil date is valid; otherwise Gregorian when valid, or returns the digit-cleaned string. Non-string/non-number values pass through unchanged.

### toISODate

```scala
val d = JalaliX.create("2025-02-25T00:30:00+03:30")

d.toISODate // 2025-02-25
d.toISODate("jalali") // 1403-12-07
```

### add / start / end

```scala
JalaliX.create("2025-02-25").addMonths(1).getStartOfMonth.format("YYYY/MM/DD")
// e.g. 1404/01/01

val d = JalaliX.create("2025-02-25", JalaliOptions(timezone = Some("Asia/Tehran")))
d.addDays(7).format("YYYY/MM/DD")
d.getEndOfWeek.format("YYYY/MM/DD")
```

### Persian / Arabic digits

```scala
JalaliX.toPersian("1403/12/07") // ۱۴۰۳/۱۲/۰۷
JalaliX.fromPersian("۱۴۰۳/۱۲/۰۷") // 1403/12/07
JalaliX.fromPersian("١٤٠٣/١٢/٠٧") // 1403/12/07 (Arabic-Indic)
```

## Timezone

Internal storage is always a **UTC Unix millisecond instant** plus an IANA timezone id (`ZoneId`). `setTimezone` is **instant-preserving**: `getTime` stays the same; wall clock / Jalali day may change.

```scala
val d = JalaliX.create(1740430800000L)

val tehran = d.setTimezone("Asia/Tehran")
tehran.format("YYYY/MM/DD") // 1403/12/07
tehran.format("HH:mm") // 00:30

val utc = d.setTimezone("UTC")
utc.format("YYYY/MM/DD") // 1403/12/06
utc.format("HH:mm") // 21:00

tehran.getTime == utc.getTime // true
```

- **Default zone:** `UTC`
- **Aliases:** `utc` / `gmt` → `UTC`; `local` / `default` / `system` → `ZoneId.systemDefault`
- **Invalid zone** → `IllegalArgumentException`

```scala
JalaliX.create(System.currentTimeMillis(), JalaliOptions(timezone = Some("Asia/Tehran")))
```

## Months

Easy to get wrong:

- `getMonth` → **1…12** (Farvardin = 1)
- `setMonth(m)` / `setFullYear(y, m)` → **0…11**, same as JavaScript `Date` (Farvardin = 0)

```scala
val d = JalaliX.create(System.currentTimeMillis())
d.setFullYear(1404, 0, 1) // 1 Farvardin 1404
d.getMonth // 1
```

## Format tokens

| Token               | Meaning                         |
| ------------------- | ------------------------------- |
| `YYYY` `YY`         | year                            |
| `MMM` `MM` `M`      | month name / number             |
| `DD` `D`            | day                             |
| `HH` `hh` `mm` `ss` | time                            |
| `A` `a`             | meridiem (ق.ظ. / ب.ظ. or AM/PM) |
| `WN` `WL`           | weekday name / letter           |
| `w` `DDD`           | week of year / day of year      |
| `X` `x`             | unix seconds / ms               |

## API

Types: `JalaliOptions` (`timezone`, `jalaliYearMin`, `jalaliYearMax`), `JalaliFormatOptions` (`calendar`, `persianDigits`).

### Constructors

| API                              | Description                          |
| -------------------------------- | ------------------------------------ |
| `new JalaliX()`                  | Current instant in UTC.              |
| `new JalaliX(epochMs: Long)`     | From Unix epoch milliseconds.        |
| `new JalaliX(isoOrDate: String)` | From ISO-8601 / `YYYY-MM-DD` string. |
| `new JalaliX(instant: Instant)`  | From `java.time.Instant`.            |

### `JalaliX` companion - values

| API              | Description                                     |
| ---------------- | ----------------------------------------------- |
| `persianNumbers` | Array of Persian digit characters `۰`…`۹`.      |
| `arabicNumbers`  | Array of Arabic-Indic digit characters `٠`…`٩`. |

### `JalaliX` companion - methods

| API                                                              | Description                                                          |
| ---------------------------------------------------------------- | -------------------------------------------------------------------- |
| `create(date: Any)`                                              | Build from unix ms, ISO string, `JalaliX`, or temporal (no options). |
| `create(date: Any, options: JalaliOptions)`                      | Build with explicit options.                                         |
| `create(date: Any, options: Option[JalaliOptions])`              | Build with optional timezone / year band.                            |
| `parseJalali(value: String)`                                     | Parse Jalali date string (no options).                               |
| `parseJalali(value: String, options: JalaliOptions)`             | Parse with explicit options.                                         |
| `parseJalali(value: String, options: Option[JalaliOptions])`     | Parse with optional options.                                         |
| `normalizeQueryDate(value: Any)`                                 | Soft-normalize query values (no options).                            |
| `normalizeQueryDate(value: Any, options: JalaliOptions)`         | Soft-normalize with explicit options.                                |
| `normalizeQueryDate(value: Any, options: Option[JalaliOptions])` | Soft-normalize with optional timezone and year band.                 |
| `isValidDate(date: Any)`                                         | `true` if the value is a supported date type.                        |
| `toPersian(str: String)`                                         | Replace Western digits with Persian digits.                          |
| `fromPersian(str: String)`                                       | Replace Persian and Arabic-Indic digits with Western digits.         |
| `compare(date: JalaliX, comparing: JalaliX)`                     | `true` if the first is strictly before the second.                   |

### Instance - getters

| API               | Description                                                           |
| ----------------- | --------------------------------------------------------------------- |
| `getFullYear`     | Jalali year in the active timezone.                                   |
| `getMonth`        | Jalali month **1…12** (Farvardin = 1).                                |
| `getDate`         | Jalali day of month **1…31**.                                         |
| `getDay`          | Weekday **1…7** (Saturday = 1 … Friday = 7).                          |
| `getHours`        | Wall-clock hours in the active timezone.                              |
| `getMinutes`      | Wall-clock minutes.                                                   |
| `getSeconds`      | Wall-clock seconds.                                                   |
| `getMilliseconds` | Wall-clock milliseconds.                                              |
| `getTime`         | Unix timestamp in milliseconds (UTC instant).                         |
| `getTimezone`     | Active IANA timezone id.                                              |
| `getWeekOfYear`   | Jalali week-of-year (1-based).                                        |
| `getDayOfYear`    | Day of Jalali year (1…365/366).                                       |
| `getDayISO`       | ISO-style weekday index used internally for week math.                |
| `getHoursISO`     | Hours in 0…23 for formatting helpers.                                 |
| `getMeridiem`     | Localized meridiem string (ق.ظ./ب.ظ. or AM/PM depending on calendar). |

### Instance - setters

| API                                                             | Description                                             |
| --------------------------------------------------------------- | ------------------------------------------------------- |
| `setTime(t: Long)`                                              | Set the UTC instant from unix milliseconds.             |
| `setMilliseconds(ms: Int)`                                      | Set milliseconds (wall clock).                          |
| `setSeconds(sec: Int)`                                          | Set seconds; keep current milliseconds.                 |
| `setSeconds(sec: Int, ms: Int)`                                 | Set seconds and milliseconds.                           |
| `setMinutes(min: Int)`                                          | Set minutes; keep current lower units.                  |
| `setMinutes(min: Int, sec: Int)`                                | Set minutes and seconds.                                |
| `setMinutes(min: Int, sec: Int, ms: Int)`                       | Set minutes, seconds, and milliseconds.                 |
| `setHours(hours: Int)`                                          | Set hours; keep current lower units.                    |
| `setHours(hours: Int, min: Int)`                                | Set hours and minutes.                                  |
| `setHours(hours: Int, min: Int, sec: Int)`                      | Set hours, minutes, and seconds.                        |
| `setHours(hours: Int, min: Int, sec: Int, ms: Int)`             | Set full wall-clock time.                               |
| `setDate(date: Int)`                                            | Set Jalali day of month.                                |
| `setMonth(month: Int)`                                          | Set Jalali month **0…11** (Farvardin = 0).              |
| `setMonth(month: Int, date: Option[Int])`                       | Set Jalali month **0…11** with optional day.            |
| `setMonth(month: Int, date: Int)`                               | Set Jalali month **0…11** with required day.            |
| `setFullYear(year: Int)`                                        | Set Jalali year only.                                   |
| `setFullYear(year: Int, month: Int)`                            | Set Jalali year and month **0…11**.                     |
| `setFullYear(year: Int, month: Int, date: Int)`                 | Set Jalali year, month **0…11**, and day.               |
| `setFullYear(year: Int, month: Option[Int], date: Option[Int])` | Set Jalali year with optional month and day.            |
| `setTimezone(tz: String)`                                       | Instant-preserving zone change; returns a new instance. |

### Instance - math, range, format

| API                                                               | Description                                                     |
| ----------------------------------------------------------------- | --------------------------------------------------------------- |
| `addYears(year: Int)`                                             | Add calendar years (mutates / returns `this`).                  |
| `addMonths(month: Int)`                                           | Add calendar months.                                            |
| `addWeeks(weeks: Int)`                                            | Add weeks.                                                      |
| `addDays(days: Int)`                                              | Add days.                                                       |
| `getStartOfYear`                                                  | Start of Jalali year (mutates / returns `this`).                |
| `getStartOfMonth`                                                 | Start of Jalali month.                                          |
| `getStartOfWeek`                                                  | Start of week.                                                  |
| `getStartOfDay`                                                   | Start of day.                                                   |
| `getEndOfYear`                                                    | End of Jalali year.                                             |
| `getEndOfMonth`                                                   | End of Jalali month.                                            |
| `getEndOfWeek`                                                    | End of week.                                                    |
| `getEndOfDay`                                                     | End of day.                                                     |
| `format(str: String)`                                             | Format with defaults.                                           |
| `format(str: String, persianDigits: Boolean)`                     | Format with optional Persian digits.                            |
| `format(str: String, options: JalaliFormatOptions)`               | Format with calendar and digit options.                         |
| `format(str: String, persianDigits: Boolean, gregorian: Boolean)` | Positional format flags.                                        |
| `toISODate`                                                       | Gregorian `YYYY-MM-DD` (default).                               |
| `toISODate(calendar: String)`                                     | `YYYY-MM-DD` in `"gregorian"` or `"jalali"`.                    |
| `clone()`                                                         | Copy with the same instant and timezone (TypeScript-style API). |
| `toString`                                                        | Human-readable Jalali date/time string.                         |
| `toDateString`                                                    | Jalali weekday + date string.                                   |
| `toLocaleDateString`                                              | `YYYY/MM/DD` in Jalali.                                         |
| `toLocaleTimeString`                                              | 12-hour time with meridiem.                                     |

## Links

- https://jalalix.com
- https://github.com/jalalix/scala
- [CHANGELOG.md](./CHANGELOG.md)

## License

MIT
