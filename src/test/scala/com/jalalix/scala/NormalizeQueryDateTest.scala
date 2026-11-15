package com.jalalix.scala

import org.scalatest.funsuite.AnyFunSuite

class NormalizeQueryDateTest extends AnyFunSuite {

  test("fromPersian converts Persian and Arabic-Indic digits") {
    assert(JalaliX.toPersian("1403/12/07") == "۱۴۰۳/۱۲/۰۷")
    assert(JalaliX.fromPersian("۱۴۰۳/۱۲/۰۷") == "1403/12/07")
    assert(JalaliX.fromPersian("١٤٠٣/١٢/٠٧") == "1403/12/07")
  }

  test("parseJalali accepts dot separator") {
    assert(JalaliX.parseJalali("1403.12.07").toISODate == "2025-02-25")
  }

  test("normalizeQueryDate sentinels and timestamps") {
    assert(JalaliX.normalizeQueryDate(null) == null)
    assert(JalaliX.normalizeQueryDate("null") == null)
    assert(JalaliX.normalizeQueryDate("") == null)
    assert(JalaliX.normalizeQueryDate("undefined") == null)
    assert(JalaliX.normalizeQueryDate("Invalid Date") == null)
    assert(JalaliX.normalizeQueryDate("NaN") == null)
    assert(JalaliX.normalizeQueryDate(0) == null)
    assert(JalaliX.normalizeQueryDate(-1) == null)

    val seconds = 1740430800L
    val utc = Some(JalaliOptions(timezone = Some("UTC")))
    val tehran = Some(JalaliOptions(timezone = Some("Asia/Tehran")))

    assert(JalaliX.normalizeQueryDate(seconds, utc) == "2025-02-24 21:00:00")
    assert(JalaliX.normalizeQueryDate(seconds * 1000, tehran) == "2025-02-25 00:30:00")
  }

  test("normalizeQueryDate Jalali and Gregorian") {
    val utc = Some(JalaliOptions(timezone = Some("UTC")))

    assert(JalaliX.normalizeQueryDate("1403/12/07", utc) == "2025-02-25 00:00:00")
    assert(
      JalaliX.normalizeQueryDate("۱۴۰۳-۱۲-۰۷ 14:30:00", utc) == "2025-02-25 14:30:00"
    )
    assert(JalaliX.normalizeQueryDate("2025.02.25") == "2025-02-25 00:00:00")
    assert(JalaliX.normalizeQueryDate("2025-02-25 12:30:45.123Z") == "2025-02-25 12:30:45")
    assert(JalaliX.normalizeQueryDate("not-a-date") == "not-a-date")

    assert(JalaliX.normalizeQueryDate("1200/01/01", utc) != null)
    assert(JalaliX.normalizeQueryDate("1800/01/01", utc) != null)

    assert(JalaliX.normalizeQueryDate("2025-01-01") == "2025-01-01 00:00:00")

    val widened =
      Some(JalaliOptions(timezone = Some("UTC"), jalaliYearMin = Some(1), jalaliYearMax = Some(3000)))

    assert(
      JalaliX.normalizeQueryDate("2025-01-01", widened) != "2025-01-01 00:00:00"
    )
  }
}