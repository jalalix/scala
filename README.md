# JalaliX (Scala)

Jalali (Persian / Shamsi) calendar for Scala 2.13+. Pure `java.time` (`ZoneId`), no runtime dependencies beyond the JDK.

<!-- jalalix:package lang=scala maven=com.jalalix.scala:jalalix api=JalaliX parity=golden -->

```scala
import com.jalalix.scala.{JalaliX, JalaliFormatOptions}

val date = JalaliX.create("2025-02-25")

date.format("YYYY/MM/DD") // 1403/12/07
date.format("DD MMM YYYY", JalaliFormatOptions(persianDigits = true)) // ۰۷ اسفند ۱۴۰۳

JalaliX.parseJalali("1403/12/07").toISODate // 2025-02-25
```

## Install

Maven coordinates: `com.jalalix.scala:jalalix:0.1.0`

```bash
./sbt test
./sbt publishLocal
./sbt publishSigned sonaUpload
```

## Timezone

Internal storage is a **UTC Unix millisecond instant** plus an IANA timezone id (`ZoneId`). `setTimezone` is **instant-preserving**: `getTime` stays the same; wall clock / Jalali day may change.

```scala
val d = JalaliX.create(1740430800000L)
val tehran = d.setTimezone("Asia/Tehran")
tehran.format("YYYY/MM/DD") // 1403/12/07
tehran.format("HH:mm:ss")   // 00:30:00

val utc = d.setTimezone("UTC")
utc.format("YYYY/MM/DD") // 1403/12/06
```

- **Default zone:** `UTC` (portable; pass `JalaliOptions(Some("Asia/Tehran"))` or `setTimezone`)
- **Aliases:** `utc` / `gmt` -> `UTC`; `local` / `default` / `system` -> `ZoneId.systemDefault`
- **Invalid zone** -> `IllegalArgumentException`

## Months

- `getMonth` -> 1..12 (Farvardin = 1)
- `setMonth(m)` / `setFullYear(y, m)` -> 0..11 (Farvardin = 0), same as JS `Date`

## License

MIT
